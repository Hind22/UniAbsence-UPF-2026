package com.uniabsence.backend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.uniabsence.backend.config.DatabaseConfig;
import com.uniabsence.backend.dto.AttendanceStatsResponse;
import com.uniabsence.backend.dto.PlannedSessionRequest;
import com.uniabsence.backend.dto.PlannedSessionResponse;
import com.uniabsence.backend.repository.PlannedSessionRepository;
import com.uniabsence.backend.repository.PlannedSessionRepository.AbsenceCounts;

@Service
public class AttendanceStatsService {

	public static final String REFERENCE_PERIOD_LABEL = "Semestre 1 (2026-2027)";
	public static final String REFERENCE_PERIOD_START = "2026-10-01";
	public static final String REFERENCE_PERIOD_END = "2026-11-30";
	public static final String DEFAULT_ACADEMIC_YEAR = "2026-2027";

	private final PlannedSessionRepository plannedSessionRepository;
	private final DatabaseConfig databaseConfig;

	public AttendanceStatsService(
			PlannedSessionRepository plannedSessionRepository,
			DatabaseConfig databaseConfig) {
		this.plannedSessionRepository = plannedSessionRepository;
		this.databaseConfig = databaseConfig;
	}

	public AttendanceStatsResponse getStatsForStudent(long studentId) {
		return getStatsForStudent(studentId, REFERENCE_PERIOD_START, REFERENCE_PERIOD_END);
	}

	public AttendanceStatsResponse getStatsForStudent(long studentId, String startDate, String endDate) {
		try (Connection connection = databaseConfig.openConnection()) {
			String groupId;
			String academicYear;

			try (PreparedStatement statement = connection.prepareStatement(
					"SELECT group_id, academic_year FROM students WHERE id = ?")) {
				statement.setLong(1, studentId);
				try (ResultSet resultSet = statement.executeQuery()) {
					if (!resultSet.next()) {
						throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Étudiant " + studentId + " introuvable.");
					}
					groupId = resultSet.getString("group_id");
					academicYear = resultSet.getString("academic_year");
					if (academicYear == null || academicYear.isBlank()) {
						academicYear = DEFAULT_ACADEMIC_YEAR;
					}
				}
			}

			AbsenceCounts counts = plannedSessionRepository.countAbsences(studentId, startDate, endDate);
			int distinctMissed = plannedSessionRepository.countDistinctMissedSessions(
				studentId,
				startDate,
				endDate
			);

			if (groupId == null || groupId.isBlank()) {
				return new AttendanceStatsResponse(
					studentId,
					null,
					REFERENCE_PERIOD_LABEL,
					startDate,
					endDate,
					0,
					distinctMissed,
					counts.total(),
					counts.justified(),
					counts.unjustified(),
					null,
					"Taux non disponible : l'étudiant n'est affecté à aucun groupe."
				);
			}

			int plannedCount = plannedSessionRepository.countDistinctPlannedSessions(
				groupId,
				academicYear,
				startDate,
				endDate
			);

			if (plannedCount <= 0) {
				return new AttendanceStatsResponse(
					studentId,
					groupId,
					REFERENCE_PERIOD_LABEL,
					startDate,
					endDate,
					0,
					distinctMissed,
					counts.total(),
					counts.justified(),
					counts.unjustified(),
					null,
					"Taux non disponible : aucune séance prévue n’est enregistrée pour cette période de référence."
				);
			}

			double rawRate = ((double) distinctMissed / plannedCount) * 100.0;
			double roundedRate = Math.round(rawRate * 10.0) / 10.0;

			String explanation = String.format(
				Locale.FRENCH,
				"Taux calculé : %d séance(s) manquée(s) sur %d séance(s) prévues (%s).",
				distinctMissed,
				plannedCount,
				REFERENCE_PERIOD_LABEL
			);

			return new AttendanceStatsResponse(
				studentId,
				groupId,
				REFERENCE_PERIOD_LABEL,
				startDate,
				endDate,
				plannedCount,
				distinctMissed,
				counts.total(),
				counts.justified(),
				counts.unjustified(),
				roundedRate,
				explanation
			);
		} catch (SQLException exception) {
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Une erreur est survenue lors du calcul du taux d'absence.",
				exception
			);
		}
	}

	public List<PlannedSessionResponse> getPlannedSessionsForStudent(long studentId) {
		try (Connection connection = databaseConfig.openConnection()) {
			String groupId;
			String academicYear;
			try (PreparedStatement statement = connection.prepareStatement(
					"SELECT group_id, academic_year FROM students WHERE id = ?")) {
				statement.setLong(1, studentId);
				try (ResultSet resultSet = statement.executeQuery()) {
					if (!resultSet.next()) {
						throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Étudiant " + studentId + " introuvable.");
					}
					groupId = resultSet.getString("group_id");
					academicYear = resultSet.getString("academic_year");
					if (academicYear == null || academicYear.isBlank()) {
						academicYear = DEFAULT_ACADEMIC_YEAR;
					}
				}
			}
			if (groupId == null || groupId.isBlank()) {
				return List.of();
			}
			return plannedSessionRepository.findByGroupIdAndPeriod(
				groupId,
				academicYear,
				REFERENCE_PERIOD_START,
				REFERENCE_PERIOD_END
			);
		} catch (SQLException exception) {
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Une erreur est survenue lors du chargement des séances prévues.",
				exception
			);
		}
	}

	public List<PlannedSessionResponse> getPlannedSessions(
			String groupId,
			String academicYear,
			String startDate,
			String endDate) {
		try {
			if (groupId != null && !groupId.isBlank()) {
				String year = (academicYear == null || academicYear.isBlank()) ? DEFAULT_ACADEMIC_YEAR : academicYear;
				String start = (startDate == null || startDate.isBlank()) ? REFERENCE_PERIOD_START : startDate;
				String end = (endDate == null || endDate.isBlank()) ? REFERENCE_PERIOD_END : endDate;
				return plannedSessionRepository.findByGroupIdAndPeriod(groupId, year, start, end);
			}
			return plannedSessionRepository.findAll();
		} catch (SQLException exception) {
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Une erreur est survenue lors de l'accès aux séances prévues.",
				exception
			);
		}
	}

	public PlannedSessionResponse createPlannedSession(PlannedSessionRequest request) {
		if (request == null || request.groupId() == null || request.sessionDate() == null
				|| request.startTime() == null || request.endTime() == null || request.subject() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tous les champs obligatoires de la séance doivent être renseignés.");
		}
		try {
			return plannedSessionRepository.create(request);
		} catch (SQLException exception) {
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Impossible d'enregistrer la séance prévue.",
				exception
			);
		}
	}
}
