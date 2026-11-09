package com.uniabsence.backend.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.uniabsence.backend.config.DatabaseConfig;
import com.uniabsence.backend.dto.JustificationRequest;
import com.uniabsence.backend.dto.JustificationResponse;

@Repository
public class JustificationRepository {

	private static final String SELECT_COLUMNS = """
		SELECT j.id, j.absence_id, a.student_id, j.file_name, j.file_path, j.status,
		       j.submitted_at, j.reviewed_at, j.reviewed_by_user_id
		FROM justifications j
		JOIN absences a ON a.id = j.absence_id
		JOIN students s ON s.id = a.student_id
		""";

	private final DatabaseConfig databaseConfig;

	public JustificationRepository(DatabaseConfig databaseConfig) {
		this.databaseConfig = databaseConfig;
	}

	public List<JustificationResponse> findAll(Long studentId, Long absenceId) throws SQLException {
		StringBuilder sql = new StringBuilder(SELECT_COLUMNS);
		List<Long> parameters = new ArrayList<>();
		List<String> conditions = new ArrayList<>();
		if (studentId != null) {
			conditions.add("a.student_id = ?");
			parameters.add(studentId);
		}
		if (absenceId != null) {
			conditions.add("j.absence_id = ?");
			parameters.add(absenceId);
		}
		if (!conditions.isEmpty()) {
			sql.append(" WHERE ").append(String.join(" AND ", conditions));
		}
		sql.append(" ORDER BY j.submitted_at DESC, j.id DESC");

		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql.toString())) {
			for (int index = 0; index < parameters.size(); index++) {
				statement.setLong(index + 1, parameters.get(index));
			}
			try (ResultSet resultSet = statement.executeQuery()) {
				List<JustificationResponse> justifications = new ArrayList<>();
				while (resultSet.next()) {
					justifications.add(mapJustification(resultSet));
				}
				return justifications;
			}
		}
	}

	public List<JustificationResponse> findByStudentUserId(long userId) throws SQLException {
		return findScoped("""
			WHERE s.user_id = ?
			""", userId);
	}

	public List<JustificationResponse> findForTeacherUserId(long userId) throws SQLException {
		return findScoped("""
			WHERE EXISTS (
				SELECT 1
				FROM teacher_groups tg
				JOIN teachers t ON t.id = tg.teacher_id
				WHERE tg.group_id = s.group_id AND t.user_id = ?
			)
			""", userId);
	}

	private List<JustificationResponse> findScoped(String scope, long userId) throws SQLException {
		String sql = SELECT_COLUMNS + scope + " ORDER BY j.submitted_at DESC, j.id DESC";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, userId);
			try (ResultSet resultSet = statement.executeQuery()) {
				List<JustificationResponse> justifications = new ArrayList<>();
				while (resultSet.next()) {
					justifications.add(mapJustification(resultSet));
				}
				return justifications;
			}
		}
	}

	public Optional<JustificationResponse> findById(long id) throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			return findById(connection, id);
		}
	}

	public JustificationResponse create(JustificationRequest request) throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			connection.setAutoCommit(false);
			try {
				requireAbsenceWithStudent(connection, request.absenceId());
				String sql = """
					INSERT INTO justifications (absence_id, file_name, file_path)
					VALUES (?, ?, ?)
					""";
				try (PreparedStatement statement = connection.prepareStatement(sql)) {
					bindJustification(statement, request);
					statement.executeUpdate();
				}
				long id;
				try (Statement statement = connection.createStatement();
					 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
					if (!resultSet.next()) {
						throw new SQLException("SQLite n'a pas retourné l'identifiant du justificatif.");
					}
					id = resultSet.getLong(1);
				}
				JustificationResponse created = findById(connection, id)
					.orElseThrow(() -> new SQLException("Le justificatif créé est introuvable."));
				connection.commit();
				return created;
			} catch (SQLException | RuntimeException exception) {
				rollback(connection, exception);
				throw exception;
			}
		}
	}

	public Optional<JustificationResponse> update(long id, JustificationRequest request) throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			connection.setAutoCommit(false);
			try {
				JustificationResponse current = findById(connection, id).orElse(null);
				if (current == null) {
					connection.rollback();
					return Optional.empty();
				}
				requirePending(current);
				requireAbsenceWithStudent(connection, request.absenceId());
				String sql = """
					UPDATE justifications
					SET absence_id = ?, file_name = ?, file_path = ?
					WHERE id = ? AND status = 'pending'
					""";
				try (PreparedStatement statement = connection.prepareStatement(sql)) {
					bindJustification(statement, request);
					statement.setLong(4, id);
					if (statement.executeUpdate() == 0) {
						throw new AlreadyReviewedException();
					}
				}
				JustificationResponse updated = findById(connection, id)
					.orElseThrow(() -> new SQLException("Le justificatif modifié est introuvable."));
				connection.commit();
				return Optional.of(updated);
			} catch (SQLException | RuntimeException exception) {
				rollback(connection, exception);
				throw exception;
			}
		}
	}

	public Optional<JustificationResponse> decide(
			long id,
			String status,
			Long reviewedByUserId) throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			connection.setAutoCommit(false);
			try {
				JustificationResponse current = findById(connection, id).orElse(null);
				if (current == null) {
					connection.rollback();
					return Optional.empty();
				}
				requirePending(current);
				if (reviewedByUserId != null && !userExists(connection, reviewedByUserId)) {
					throw new InvalidJustificationReferenceException(
						"L'utilisateur ayant validé le justificatif n'existe pas."
					);
				}

				try (PreparedStatement statement = connection.prepareStatement("""
						UPDATE justifications
						SET status = ?, reviewed_at = CURRENT_TIMESTAMP, reviewed_by_user_id = ?
						WHERE id = ? AND status = 'pending'
						""")) {
					statement.setString(1, status);
					if (reviewedByUserId == null) {
						statement.setNull(2, Types.INTEGER);
					} else {
						statement.setLong(2, reviewedByUserId);
					}
					statement.setLong(3, id);
					if (statement.executeUpdate() == 0) {
						throw new AlreadyReviewedException();
					}
				}

				String absenceStatus = "accepted".equals(status) ? "justified" : "unjustified";
				try (PreparedStatement statement = connection.prepareStatement(
						"UPDATE absences SET status = ? WHERE id = ?")) {
					statement.setString(1, absenceStatus);
					statement.setLong(2, current.absenceId());
					if (statement.executeUpdate() == 0) {
						throw new InvalidJustificationReferenceException(
							"L'absence associée au justificatif n'existe plus."
						);
					}
				}

				JustificationResponse decided = findById(connection, id)
					.orElseThrow(() -> new SQLException("Le justificatif validé est introuvable."));
				connection.commit();
				return Optional.of(decided);
			} catch (SQLException | RuntimeException exception) {
				rollback(connection, exception);
				throw exception;
			}
		}
	}

	public DeleteResult deleteIfPending(long id) throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			connection.setAutoCommit(false);
			try {
				JustificationResponse current = findById(connection, id).orElse(null);
				if (current == null) {
					connection.rollback();
					return DeleteResult.NOT_FOUND;
				}
				requirePending(current);
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM justifications WHERE id = ? AND status = 'pending'")) {
					statement.setLong(1, id);
					if (statement.executeUpdate() == 0) {
						throw new AlreadyReviewedException();
					}
				}
				connection.commit();
				return DeleteResult.DELETED;
			} catch (SQLException | RuntimeException exception) {
				rollback(connection, exception);
				throw exception;
			}
		}
	}

	public boolean absenceExists(long absenceId) throws SQLException {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement("""
				SELECT 1
				FROM absences a
				JOIN students s ON s.id = a.student_id
				WHERE a.id = ?
				""")) {
			statement.setLong(1, absenceId);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	public boolean studentExists(long studentId) throws SQLException {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(
					"SELECT 1 FROM students WHERE id = ?")) {
			statement.setLong(1, studentId);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private Optional<JustificationResponse> findById(Connection connection, long id) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS + " WHERE j.id = ?")) {
			statement.setLong(1, id);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next()
					? Optional.of(mapJustification(resultSet))
					: Optional.empty();
			}
		}
	}

	private void requireAbsenceWithStudent(Connection connection, long absenceId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT 1
				FROM absences a
				JOIN students s ON s.id = a.student_id
				WHERE a.id = ?
				""")) {
			statement.setLong(1, absenceId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new InvalidJustificationReferenceException(
						"L'absence ou l'étudiant associé n'existe pas."
					);
				}
			}
		}
	}

	private boolean userExists(Connection connection, long userId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM users WHERE id = ?")) {
			statement.setLong(1, userId);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private void requirePending(JustificationResponse justification) {
		if (!"pending".equals(justification.status())) {
			throw new AlreadyReviewedException();
		}
	}

	private void bindJustification(PreparedStatement statement, JustificationRequest request)
			throws SQLException {
		statement.setLong(1, request.absenceId());
		statement.setString(2, request.fileName());
		statement.setString(3, request.filePath());
	}

	private JustificationResponse mapJustification(ResultSet resultSet) throws SQLException {
		long reviewedByValue = resultSet.getLong("reviewed_by_user_id");
		Long reviewedByUserId = resultSet.wasNull() ? null : reviewedByValue;
		return new JustificationResponse(
			resultSet.getLong("id"),
			resultSet.getLong("absence_id"),
			resultSet.getLong("student_id"),
			resultSet.getString("file_name"),
			resultSet.getString("file_path"),
			resultSet.getString("status"),
			resultSet.getString("submitted_at"),
			resultSet.getString("reviewed_at"),
			reviewedByUserId
		);
	}

	private void rollback(Connection connection, Exception exception) {
		try {
			connection.rollback();
		} catch (SQLException rollbackException) {
			exception.addSuppressed(rollbackException);
		}
	}

	public enum DeleteResult {
		DELETED,
		NOT_FOUND
	}

	public static class AlreadyReviewedException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	public static class InvalidJustificationReferenceException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public InvalidJustificationReferenceException(String message) {
			super(message);
		}
	}
}
