package com.uniabsence.backend.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.uniabsence.backend.dto.GroupRequest;
import com.uniabsence.backend.dto.GroupResponse;
import com.uniabsence.backend.repository.GroupRepository;
import com.uniabsence.backend.repository.GroupRepository.DeleteResult;

@Service
public class GroupService {

	private final GroupRepository groupRepository;

	public GroupService(GroupRepository groupRepository) {
		this.groupRepository = groupRepository;
	}

	public List<GroupResponse> findAll() {
		try {
			return groupRepository.findAll();
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public List<GroupResponse> findForTeacherUserId(long userId) {
		try {
			return groupRepository.findForTeacherUserId(userId);
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public GroupResponse findById(String id) {
		String normalizedId = normalizeId(id);
		try {
			return groupRepository.findById(normalizedId)
				.orElseThrow(() -> notFound(normalizedId));
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public GroupResponse create(GroupRequest request) {
		GroupRequest group = normalizeAndValidate(request, true);
		try {
			return groupRepository.create(group);
		} catch (SQLException exception) {
			throw constraintError(exception);
		}
	}

	public GroupResponse update(String id, GroupRequest request) {
		String normalizedId = normalizeId(id);
		GroupRequest group = normalizeAndValidate(request, false);
		try {
			return groupRepository.update(normalizedId, group)
				.orElseThrow(() -> notFound(normalizedId));
		} catch (SQLException exception) {
			throw constraintError(exception);
		}
	}

	public void delete(String id) {
		String normalizedId = normalizeId(id);
		try {
			switch (groupRepository.deleteIfUnused(normalizedId)) {
				case DELETED -> {
				}
				case NOT_FOUND -> throw notFound(normalizedId);
				case IN_USE -> throw new ResponseStatusException(
					HttpStatus.CONFLICT,
					"Le groupe contient encore des étudiants ou des enseignants affectés."
				);
			}
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	private GroupRequest normalizeAndValidate(GroupRequest request, boolean requireId) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le corps de la requête est obligatoire.");
		}
		String id = requireId ? normalizeId(request.id()) : null;
		String name = normalizeRequired(request.name(), "name");
		return new GroupRequest(
			id,
			name,
			normalizeOptional(request.specialty()),
			normalizeOptional(request.mainSubject()),
			normalizeOptional(request.academicYear())
		);
	}

	private String normalizeId(String id) {
		String value = normalizeRequired(id, "id");
		return value.toUpperCase(Locale.ROOT);
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

	private ResponseStatusException notFound(String id) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Groupe " + id + " introuvable.");
	}

	private ResponseStatusException constraintError(SQLException exception) {
		String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
		if (message.contains("unique constraint failed") || message.contains("primary key")) {
			return new ResponseStatusException(
				HttpStatus.CONFLICT,
				"Un groupe avec cet identifiant existe déjà.",
				exception
			);
		}
		if (message.contains("foreign key constraint failed")) {
			return new ResponseStatusException(
				HttpStatus.CONFLICT,
				"Le groupe est encore référencé par des étudiants ou des enseignants.",
				exception
			);
		}
		return databaseError(exception);
	}

	private ResponseStatusException databaseError(SQLException exception) {
		return new ResponseStatusException(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"Une erreur est survenue lors de l'accès aux groupes.",
			exception
		);
	}
}
