package com.uniabsence.backend.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.uniabsence.backend.dto.StudentRequest;
import com.uniabsence.backend.dto.StudentResponse;
import com.uniabsence.backend.repository.StudentRepository;

@Service
public class StudentService {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private final StudentRepository studentRepository;

	public StudentService(StudentRepository studentRepository) {
		this.studentRepository = studentRepository;
	}

	public List<StudentResponse> findAll() {
		try {
			return studentRepository.findAll();
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public List<StudentResponse> findForTeacherUserId(long userId) {
		try {
			return studentRepository.findForTeacherUserId(userId);
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public StudentResponse findById(long id) {
		try {
			return studentRepository.findById(id)
				.orElseThrow(() -> notFound(id));
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public StudentResponse create(StudentRequest request) {
		StudentRequest student = normalizeAndValidate(request);
		try {
			return studentRepository.create(student);
		} catch (SQLException exception) {
			throw constraintError(exception);
		}
	}

	public StudentResponse update(long id, StudentRequest request) {
		StudentRequest student = normalizeAndValidate(request);
		try {
			return studentRepository.update(id, student)
				.orElseThrow(() -> notFound(id));
		} catch (SQLException exception) {
			throw constraintError(exception);
		}
	}

	public void delete(long id) {
		try {
			if (!studentRepository.delete(id)) {
				throw notFound(id);
			}
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	private StudentRequest normalizeAndValidate(StudentRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le corps de la requête est obligatoire.");
		}

		String studentNumber = normalizeRequired(request.studentNumber(), "studentNumber");
		String firstName = normalizeRequired(request.firstName(), "firstName");
		String lastName = normalizeRequired(request.lastName(), "lastName");
		String email = normalizeRequired(request.email(), "email").toLowerCase(Locale.ROOT);
		if (!EMAIL_PATTERN.matcher(email).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'adresse email n'est pas valide.");
		}
		if (request.userId() != null && request.userId() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId doit être un entier positif.");
		}

		return new StudentRequest(
			request.userId(),
			studentNumber,
			firstName,
			lastName,
			email,
			normalizeOptional(request.groupId()),
			normalizeOptional(request.program()),
			normalizeOptional(request.avatar()),
			normalizeOptional(request.academicYear())
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
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Étudiant " + id + " introuvable.");
	}

	private ResponseStatusException constraintError(SQLException exception) {
		String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
		if (message.contains("unique constraint failed")) {
			return new ResponseStatusException(
				HttpStatus.CONFLICT,
				"Le matricule ou l'adresse email est déjà utilisé.",
				exception
			);
		}
		if (message.contains("foreign key constraint failed")) {
			return new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"userId ou groupId ne référence aucun enregistrement existant.",
				exception
			);
		}
		return databaseError(exception);
	}

	private ResponseStatusException databaseError(SQLException exception) {
		return new ResponseStatusException(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"Une erreur est survenue lors de l'accès aux étudiants.",
			exception
		);
	}
}
