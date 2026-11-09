package com.uniabsence.backend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.uniabsence.backend.auth.AuthenticatedUser;
import com.uniabsence.backend.config.DatabaseConfig;
import com.uniabsence.backend.dto.AbsenceResponse;
import com.uniabsence.backend.dto.JustificationResponse;

@Service
public class AuthorizationService {

	private final DatabaseConfig databaseConfig;

	public AuthorizationService(DatabaseConfig databaseConfig) {
		this.databaseConfig = databaseConfig;
	}

	public long requireStudentProfileId(AuthenticatedUser user) {
		requireRole(user, "STUDENT");
		return findProfileId("students", user.id(), "Accès refusé à cette ressource.");
	}

	public long requireTeacherProfileId(AuthenticatedUser user) {
		requireRole(user, "TEACHER");
		return findProfileId(
			"teachers",
			user.id(),
			"Ce compte enseignant n’est pas encore associé à un profil enseignant. Contactez l’administration."
		);
	}

	public void requireStudentAccessToStudent(AuthenticatedUser user, long studentId) {
		if (requireStudentProfileId(user) != studentId) {
			throw forbidden();
		}
	}

	public void requireTeacherAccessToStudent(AuthenticatedUser user, long studentId) {
		requireTeacherProfileId(user);
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement("""
				SELECT 1
				FROM students s
				JOIN teacher_groups tg ON tg.group_id = s.group_id
				JOIN teachers t ON t.id = tg.teacher_id
				WHERE s.id = ? AND t.user_id = ?
				LIMIT 1
				""")) {
			statement.setLong(1, studentId);
			statement.setLong(2, user.id());
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw forbidden();
				}
			}
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public void requireStudentAccessToAbsence(AuthenticatedUser user, AbsenceResponse absence) {
		requireStudentAccessToStudent(user, absence.studentId());
	}

	public void requireTeacherAccessToAbsence(AuthenticatedUser user, AbsenceResponse absence) {
		requireTeacherAccessToStudent(user, absence.studentId());
	}

	public void requireStudentAccessToJustification(AuthenticatedUser user, JustificationResponse justification) {
		requireStudentAccessToStudent(user, justification.studentId());
	}

	public void requireTeacherAccessToJustification(
			AuthenticatedUser user,
			JustificationResponse justification) {
		requireTeacherAccessToStudent(user, justification.studentId());
	}

	public void requireStudentOwnsAbsence(AuthenticatedUser user, long absenceId) {
		if (absenceId <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "absenceId doit être un entier positif.");
		}
		long studentId = requireStudentProfileId(user);
		if (!exists("""
				SELECT 1 FROM absences WHERE id = ? AND student_id = ?
				""", absenceId, studentId)) {
			throw forbidden();
		}
	}

	public void requireTeacherAccessToAbsence(AuthenticatedUser user, long absenceId) {
		if (absenceId <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "absenceId doit être un entier positif.");
		}
		requireTeacherProfileId(user);
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement("""
				SELECT 1
				FROM absences a
				JOIN students s ON s.id = a.student_id
				JOIN teacher_groups tg ON tg.group_id = s.group_id
				JOIN teachers t ON t.id = tg.teacher_id
				WHERE a.id = ? AND t.user_id = ?
				LIMIT 1
				""")) {
			statement.setLong(1, absenceId);
			statement.setLong(2, user.id());
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw forbidden();
				}
			}
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	private long findProfileId(String table, long userId, String missingProfileMessage) {
		String query = "SELECT id FROM " + table + " WHERE user_id = ?";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setLong(1, userId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new ResponseStatusException(HttpStatus.FORBIDDEN, missingProfileMessage);
				}
				return resultSet.getLong("id");
			}
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	private boolean exists(String query, long firstId, long secondId) {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setLong(1, firstId);
			statement.setLong(2, secondId);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	private void requireRole(AuthenticatedUser user, String expectedRole) {
		if (user == null || !expectedRole.equals(user.role())) {
			throw forbidden();
		}
	}

	private ResponseStatusException forbidden() {
		return new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé à cette ressource.");
	}

	private ResponseStatusException databaseError(SQLException exception) {
		return new ResponseStatusException(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"Une erreur est survenue pendant la vérification des autorisations.",
			exception
		);
	}
}
