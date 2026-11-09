package com.uniabsence.backend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.uniabsence.backend.config.DatabaseConfig;
import com.uniabsence.backend.dto.TimetableResponse;

@Service
@Profile("dev")
public class TimetableService {

	private final DatabaseConfig databaseConfig;

	public TimetableService(DatabaseConfig databaseConfig) {
		this.databaseConfig = databaseConfig;
	}

	public TimetableResponse findForStudent(long studentId) {
		try (Connection connection = databaseConfig.openConnection()) {
			String groupId;
			String groupName;
			String academicYear;
			try (PreparedStatement statement = connection.prepareStatement("""
					SELECT g.id, g.name, g.academic_year
					FROM students s
					LEFT JOIN groups g ON g.id = s.group_id
					WHERE s.id = ?
					""")) {
				statement.setLong(1, studentId);
				try (ResultSet resultSet = statement.executeQuery()) {
					if (!resultSet.next()) {
						throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profil étudiant introuvable.");
					}
					groupId = resultSet.getString("id");
					groupName = resultSet.getString("name");
					academicYear = resultSet.getString("academic_year");
					if (academicYear == null || academicYear.isBlank()) {
						academicYear = "2026-2027";
					}
				}
			}

			if (groupId == null) {
				return new TimetableResponse(null, null, academicYear, false, List.of());
			}

			List<TimetableResponse.Session> sessions = new ArrayList<>();
			try (PreparedStatement statement = connection.prepareStatement("""
					SELECT ts.id, ts.day_of_week, ts.subject, ts.start_time, ts.end_time,
					       ts.session_type, ts.room, ts.is_demo,
					       CASE WHEN t.id IS NULL THEN NULL
					            ELSE TRIM(t.first_name || ' ' || t.last_name)
					       END AS teacher_name
					FROM timetable_sessions ts
					LEFT JOIN teachers t ON t.id = ts.teacher_id
					WHERE ts.group_id = ? AND ts.academic_year = ?
					ORDER BY ts.day_of_week, ts.start_time, ts.id
					""")) {
				statement.setString(1, groupId);
				statement.setString(2, academicYear);
				try (ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						boolean demonstration = resultSet.getInt("is_demo") != 0;
						sessions.add(new TimetableResponse.Session(
							resultSet.getLong("id"),
							resultSet.getInt("day_of_week"),
							dayName(resultSet.getInt("day_of_week")),
							resultSet.getString("subject"),
							resultSet.getString("start_time"),
							resultSet.getString("end_time"),
							resultSet.getString("session_type"),
							resultSet.getString("teacher_name"),
							resultSet.getString("room"),
							demonstration
						));
					}
				}
			}
			boolean demonstration = sessions.stream().anyMatch(TimetableResponse.Session::demonstration);
			return new TimetableResponse(groupId, groupName, academicYear, demonstration, sessions);
		} catch (SQLException exception) {
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Une erreur est survenue lors du chargement de l’emploi du temps.",
				exception
			);
		}
	}

	private String dayName(int dayOfWeek) {
		return switch (dayOfWeek) {
			case 1 -> "Lundi";
			case 2 -> "Mardi";
			case 3 -> "Mercredi";
			case 4 -> "Jeudi";
			case 5 -> "Vendredi";
			case 6 -> "Samedi";
			default -> throw new IllegalStateException("Jour de la semaine invalide dans l’emploi du temps.");
		};
	}
}
