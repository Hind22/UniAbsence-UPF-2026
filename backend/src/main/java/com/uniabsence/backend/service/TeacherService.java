package com.uniabsence.backend.service;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.uniabsence.backend.dto.TeacherRequest;
import com.uniabsence.backend.dto.TeacherResponse;
import com.uniabsence.backend.repository.TeacherRepository;

@Service
public class TeacherService {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private final TeacherRepository teacherRepository;

	public TeacherService(TeacherRepository teacherRepository) {
		this.teacherRepository = teacherRepository;
	}

	public List<TeacherResponse> findAll() {
		try {
			return teacherRepository.findAll();
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public TeacherResponse findById(long id) {
		try {
			return teacherRepository.findById(id)
				.orElseThrow(() -> notFound(id));
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public TeacherResponse create(TeacherRequest request) {
		TeacherRequest teacher = normalizeAndValidate(request);
		try {
			return teacherRepository.create(teacher);
		} catch (SQLException exception) {
			throw constraintError(exception);
		}
	}

	public TeacherResponse update(long id, TeacherRequest request) {
		TeacherRequest teacher = normalizeAndValidate(request);
		try {
			return teacherRepository.update(id, teacher)
				.orElseThrow(() -> notFound(id));
		} catch (SQLException exception) {
			throw constraintError(exception);
		}
	}

	public void delete(long id) {
		try {
			if (!teacherRepository.delete(id)) {
				throw notFound(id);
			}
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	private TeacherRequest normalizeAndValidate(TeacherRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le corps de la requête est obligatoire.");
		}

		String teacherIdentifier = normalizeRequired(request.teacherIdentifier(), "teacherIdentifier")
			.toUpperCase(Locale.ROOT);
		String firstName = normalizeRequired(request.firstName(), "firstName");
		String lastName = normalizeRequired(request.lastName(), "lastName");
		String email = normalizeRequired(request.email(), "email").toLowerCase(Locale.ROOT);
		if (!EMAIL_PATTERN.matcher(email).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'adresse email n'est pas valide.");
		}
		if (request.userId() != null && request.userId() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId doit être un entier positif.");
		}

		List<String> groupIds = normalizeGroupIds(request.groupIds());
		return new TeacherRequest(
			request.userId(),
			teacherIdentifier,
			firstName,
			lastName,
			email,
			normalizeOptional(request.subject()),
			normalizeOptional(request.grade()),
			normalizeOptional(request.department()),
			normalizeOptional(request.avatar()),
			normalizeOptional(request.academicYear()),
			groupIds
		);
	}

	private List<String> normalizeGroupIds(List<String> groupIds) {
		if (groupIds == null || groupIds.isEmpty()) {
			return List.of();
		}

		List<String> normalized = groupIds.stream()
			.map(groupId -> normalizeRequired(groupId, "groupIds"))
			.distinct()
			.toList();
		if (normalized.size() != groupIds.size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "groupIds ne doit pas contenir de doublons.");
		}
		Set<String> uniqueGroupIds = new HashSet<>(normalized);
		if (uniqueGroupIds.size() != normalized.size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "groupIds ne doit pas contenir de doublons.");
		}
		return normalized;
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
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Enseignant " + id + " introuvable.");
	}

	private ResponseStatusException constraintError(SQLException exception) {
		String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
		if (message.contains("unique constraint failed")) {
			return new ResponseStatusException(
				HttpStatus.CONFLICT,
				"L'identifiant enseignant ou l'adresse email est déjà utilisé.",
				exception
			);
		}
		if (message.contains("foreign key constraint failed")) {
			return new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"userId ou groupIds ne référence aucun enregistrement existant.",
				exception
			);
		}
		return databaseError(exception);
	}

	private ResponseStatusException databaseError(SQLException exception) {
		return new ResponseStatusException(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"Une erreur est survenue lors de l'accès aux enseignants.",
			exception
		);
	}
}
