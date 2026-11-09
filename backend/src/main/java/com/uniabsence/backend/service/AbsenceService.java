package com.uniabsence.backend.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.uniabsence.backend.dto.AbsenceRequest;
import com.uniabsence.backend.dto.AbsenceResponse;
import com.uniabsence.backend.repository.AbsenceRepository;
import com.uniabsence.backend.repository.AbsenceRepository.DuplicateAbsenceException;
import com.uniabsence.backend.repository.AbsenceRepository.InvalidAbsenceReferenceException;

@Service
public class AbsenceService {

	private static final Set<String> VALID_STATUSES = Set.of("justified", "unjustified");
	private static final Set<String> VALID_SESSION_TYPES = Set.of("CM", "TD", "TP");

	private final AbsenceRepository absenceRepository;

	public AbsenceService(AbsenceRepository absenceRepository) {
		this.absenceRepository = absenceRepository;
	}

	public List<AbsenceResponse> findAll() {
		try {
			return absenceRepository.findAll();
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public List<AbsenceResponse> findForStudentUserId(long userId) {
		try {
			return absenceRepository.findByStudentUserId(userId);
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public List<AbsenceResponse> findForTeacherUserId(long userId) {
		try {
			return absenceRepository.findForTeacherUserId(userId);
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public AbsenceResponse findById(long id) {
		try {
			return absenceRepository.findById(id)
				.orElseThrow(() -> notFound(id));
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public List<AbsenceResponse> findByStudentId(long studentId) {
		if (studentId <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId doit être un entier positif.");
		}
		try {
			if (!absenceRepository.studentExists(studentId)) {
				throw new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"Étudiant " + studentId + " introuvable."
				);
			}
			return absenceRepository.findByStudentId(studentId);
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public AbsenceResponse create(AbsenceRequest request) {
		AbsenceRequest absence = normalizeAndValidate(request);
		try {
			return absenceRepository.create(absence);
		} catch (DuplicateAbsenceException exception) {
			throw duplicateError(exception);
		} catch (InvalidAbsenceReferenceException exception) {
			throw referenceError(exception);
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public AbsenceResponse update(long id, AbsenceRequest request) {
		if (id <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'identifiant doit être positif.");
		}
		AbsenceRequest absence = normalizeAndValidate(request);
		try {
			return absenceRepository.update(id, absence)
				.orElseThrow(() -> notFound(id));
		} catch (DuplicateAbsenceException exception) {
			throw duplicateError(exception);
		} catch (InvalidAbsenceReferenceException exception) {
			throw referenceError(exception);
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public void delete(long id) {
		if (id <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'identifiant doit être positif.");
		}
		try {
			if (!absenceRepository.delete(id)) {
				throw notFound(id);
			}
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	private AbsenceRequest normalizeAndValidate(AbsenceRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le corps de la requête est obligatoire.");
		}
		if (request.studentId() == null || request.studentId() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId doit être un entier positif.");
		}
		if (request.teacherId() != null && request.teacherId() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "teacherId doit être un entier positif.");
		}

		String absenceDate = normalizeRequired(request.absenceDate(), "absenceDate");
		try {
			if (!LocalDate.parse(absenceDate).toString().equals(absenceDate)) {
				throw new DateTimeParseException("Date ISO invalide.", absenceDate, 0);
			}
		} catch (DateTimeParseException exception) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"absenceDate doit respecter le format ISO yyyy-MM-dd.",
				exception
			);
		}

		String subject = normalizeRequired(request.subject(), "subject");
		String status = request.status() == null || request.status().isBlank()
			? "unjustified"
			: request.status().trim().toLowerCase(Locale.ROOT);
		if (!VALID_STATUSES.contains(status)) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"status doit être 'justified' ou 'unjustified'."
			);
		}

		String sessionType = normalizeOptional(request.sessionType());
		if (sessionType != null) {
			sessionType = sessionType.toUpperCase(Locale.ROOT);
			if (!VALID_SESSION_TYPES.contains(sessionType)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionType doit être CM, TD ou TP.");
			}
		}

		String startTime = normalizeOptional(request.startTime());
		String endTime = normalizeOptional(request.endTime());
		if ((startTime == null) != (endTime == null)) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"startTime et endTime doivent être renseignés ensemble."
			);
		}
		if (startTime != null) {
			try {
				LocalTime start = LocalTime.parse(startTime);
				LocalTime end = LocalTime.parse(endTime);
				startTime = start.toString();
				endTime = end.toString();
				if (!start.isBefore(end)) {
					throw new ResponseStatusException(
						HttpStatus.BAD_REQUEST,
						"startTime doit être antérieur à endTime."
					);
				}
			} catch (DateTimeParseException exception) {
				throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"startTime et endTime doivent respecter le format HH:mm.",
					exception
				);
			}
		}

		String teacherName = normalizeOptional(request.teacherName());
		String sessionLabel = normalizeOptional(request.sessionLabel());
		if (sessionLabel == null && sessionType != null && startTime != null) {
			sessionLabel = sessionType + " " + startTime + "–" + endTime;
		}

		return new AbsenceRequest(
			request.studentId(),
			request.teacherId(),
			absenceDate,
			subject,
			teacherName,
			sessionLabel,
			sessionType,
			startTime,
			endTime,
			status
		);
	}

	private String normalizeRequired(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " est obligatoire.");
		}
		return value.trim();
	}

	private String normalizeOptional(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private ResponseStatusException notFound(long id) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Absence " + id + " introuvable.");
	}

	private ResponseStatusException duplicateError(DuplicateAbsenceException exception) {
		return new ResponseStatusException(
			HttpStatus.CONFLICT,
			"Une absence existe déjà pour cet étudiant à cette date, pour cette matière et cette séance.",
			exception
		);
	}

	private ResponseStatusException referenceError(InvalidAbsenceReferenceException exception) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
	}

	private ResponseStatusException databaseError(SQLException exception) {
		return new ResponseStatusException(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"Une erreur est survenue lors de l'accès aux absences.",
			exception
		);
	}
}
