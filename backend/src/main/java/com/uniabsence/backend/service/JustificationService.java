package com.uniabsence.backend.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.uniabsence.backend.dto.JustificationDecisionRequest;
import com.uniabsence.backend.dto.JustificationRequest;
import com.uniabsence.backend.dto.JustificationResponse;
import com.uniabsence.backend.repository.JustificationRepository;
import com.uniabsence.backend.repository.JustificationRepository.AlreadyReviewedException;
import com.uniabsence.backend.repository.JustificationRepository.DeleteResult;
import com.uniabsence.backend.repository.JustificationRepository.InvalidJustificationReferenceException;

@Service
public class JustificationService {

	private static final Logger logger = LoggerFactory.getLogger(JustificationService.class);
	private static final Set<String> DECISION_STATUSES = Set.of("accepted", "rejected");

	private final JustificationRepository justificationRepository;
	private final JustificationFileStorage fileStorage;

	public JustificationService(
			JustificationRepository justificationRepository,
			JustificationFileStorage fileStorage) {
		this.justificationRepository = justificationRepository;
		this.fileStorage = fileStorage;
	}

	public List<JustificationResponse> findAll(Long studentId, Long absenceId) {
		if (studentId != null && studentId <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId doit être positif.");
		}
		if (absenceId != null && absenceId <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "absenceId doit être positif.");
		}
		try {
			if (studentId != null && !justificationRepository.studentExists(studentId)) {
				throw new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"Étudiant " + studentId + " introuvable."
				);
			}
			if (absenceId != null && !justificationRepository.absenceExists(absenceId)) {
				throw new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"Absence " + absenceId + " introuvable."
				);
			}
			return justificationRepository.findAll(studentId, absenceId);
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public List<JustificationResponse> findForStudentUserId(long userId) {
		try {
			return justificationRepository.findByStudentUserId(userId);
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public List<JustificationResponse> findForTeacherUserId(long userId) {
		try {
			return justificationRepository.findForTeacherUserId(userId);
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public JustificationResponse findById(long id) {
		validateId(id);
		try {
			return justificationRepository.findById(id)
				.orElseThrow(() -> notFound(id));
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public JustificationResponse create(JustificationRequest request) {
		JustificationRequest justification = normalizeAndValidate(request);
		try {
			return justificationRepository.create(justification);
		} catch (InvalidJustificationReferenceException exception) {
			throw referenceError(exception);
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public JustificationResponse upload(Long absenceId, MultipartFile file) {
		if (absenceId == null || absenceId <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "absenceId doit être un entier positif.");
		}
		JustificationFileStorage.StoredFile stored;
		try {
			stored = fileStorage.store(file);
		} catch (java.io.IOException exception) {
			throw fileStorageError(exception);
		}
		try {
			return create(new JustificationRequest(absenceId, stored.originalName(), stored.storageKey()));
		} catch (RuntimeException exception) {
			try {
				fileStorage.delete(stored.storageKey());
			} catch (java.io.IOException cleanupException) {
				exception.addSuppressed(cleanupException);
				logger.error("Impossible de supprimer le fichier téléversé après l'échec de sa persistance.", cleanupException);
			}
			throw exception;
		}
	}

	public JustificationFileStorage.StoredFileContents readFile(JustificationResponse justification) {
		if (justification.filePath() == null || justification.filePath().isBlank()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun fichier n'est associé à ce justificatif.");
		}
		try {
			return fileStorage.read(justification.filePath(), justification.fileName());
		} catch (java.io.IOException exception) {
			throw fileStorageError(exception);
		}
	}

	public JustificationResponse update(long id, JustificationRequest request) {
		validateId(id);
		JustificationRequest justification = normalizeAndValidate(request);
		try {
			return justificationRepository.update(id, justification)
				.orElseThrow(() -> notFound(id));
		} catch (AlreadyReviewedException exception) {
			throw alreadyReviewedError(exception);
		} catch (InvalidJustificationReferenceException exception) {
			throw referenceError(exception);
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public JustificationResponse decide(long id, JustificationDecisionRequest request) {
		validateId(id);
		if (request == null || request.status() == null || request.status().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status est obligatoire.");
		}
		String status = request.status().trim().toLowerCase(Locale.ROOT);
		if (!DECISION_STATUSES.contains(status)) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"status doit être 'accepted' ou 'rejected'."
			);
		}
		if (request.reviewedByUserId() != null && request.reviewedByUserId() <= 0) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"reviewedByUserId doit être un entier positif."
			);
		}
		try {
			return justificationRepository.decide(id, status, request.reviewedByUserId())
				.orElseThrow(() -> notFound(id));
		} catch (AlreadyReviewedException exception) {
			throw alreadyReviewedError(exception);
		} catch (InvalidJustificationReferenceException exception) {
			throw referenceError(exception);
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public void delete(long id) {
		validateId(id);
		try {
			JustificationResponse justification = findById(id);
			switch (justificationRepository.deleteIfPending(id)) {
				case DELETED -> deleteStoredFile(justification.filePath());
				case NOT_FOUND -> throw notFound(id);
			}
		} catch (AlreadyReviewedException exception) {
			throw alreadyReviewedError(exception);
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	private void deleteStoredFile(String storageKey) {
		try {
			fileStorage.delete(storageKey);
		} catch (java.io.IOException exception) {
			throw fileStorageError(exception);
		}
	}

	private JustificationRequest normalizeAndValidate(JustificationRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le corps de la requête est obligatoire.");
		}
		if (request.absenceId() == null || request.absenceId() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "absenceId doit être un entier positif.");
		}
		String fileName = normalizeRequired(request.fileName(), "fileName");
		String filePath = normalizeOptional(request.filePath());
		return new JustificationRequest(request.absenceId(), fileName, filePath);
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

	private void validateId(long id) {
		if (id <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'identifiant doit être positif.");
		}
	}

	private ResponseStatusException notFound(long id) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Justificatif " + id + " introuvable.");
	}

	private ResponseStatusException alreadyReviewedError(AlreadyReviewedException exception) {
		return new ResponseStatusException(
			HttpStatus.CONFLICT,
			"Un justificatif déjà accepté ou refusé ne peut plus être modifié, supprimé ou réexaminé.",
			exception
		);
	}

	private ResponseStatusException referenceError(InvalidJustificationReferenceException exception) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
	}

	private ResponseStatusException databaseError(SQLException exception) {
		return new ResponseStatusException(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"Une erreur est survenue lors de l'accès aux justificatifs.",
			exception
		);
	}

	private ResponseStatusException fileStorageError(java.io.IOException exception) {
		logger.error("Erreur lors de l'accès au fichier justificatif.", exception);
		return new ResponseStatusException(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"Une erreur est survenue lors de l'accès au fichier justificatif.",
			exception
		);
	}
}
