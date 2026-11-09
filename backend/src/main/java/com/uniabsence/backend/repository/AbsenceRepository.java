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
import com.uniabsence.backend.dto.AbsenceRequest;
import com.uniabsence.backend.dto.AbsenceResponse;

@Repository
public class AbsenceRepository {

	private static final String SELECT_COLUMNS = """
		SELECT id, student_id, teacher_id, absence_date, subject, teacher_name,
		       session_label, session_type, start_time, end_time, status, created_at
		FROM absences
		""";

	private final DatabaseConfig databaseConfig;

	public AbsenceRepository(DatabaseConfig databaseConfig) {
		this.databaseConfig = databaseConfig;
	}

	public List<AbsenceResponse> findAll() throws SQLException {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(
					SELECT_COLUMNS + " ORDER BY absence_date DESC, id DESC");
			 ResultSet resultSet = statement.executeQuery()) {
			List<AbsenceResponse> absences = new ArrayList<>();
			while (resultSet.next()) {
				absences.add(mapAbsence(resultSet));
			}
			return absences;
		}
	}

	public List<AbsenceResponse> findByStudentId(long studentId) throws SQLException {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(
					SELECT_COLUMNS + " WHERE student_id = ? ORDER BY absence_date DESC, id DESC")) {
			statement.setLong(1, studentId);
			try (ResultSet resultSet = statement.executeQuery()) {
				List<AbsenceResponse> absences = new ArrayList<>();
				while (resultSet.next()) {
					absences.add(mapAbsence(resultSet));
				}
				return absences;
			}
		}
	}

	public List<AbsenceResponse> findByStudentUserId(long userId) throws SQLException {
		String sql = SELECT_COLUMNS + """
			WHERE student_id = (SELECT id FROM students WHERE user_id = ?)
			ORDER BY absence_date DESC, id DESC
			""";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, userId);
			try (ResultSet resultSet = statement.executeQuery()) {
				List<AbsenceResponse> absences = new ArrayList<>();
				while (resultSet.next()) {
					absences.add(mapAbsence(resultSet));
				}
				return absences;
			}
		}
	}

	public List<AbsenceResponse> findForTeacherUserId(long userId) throws SQLException {
		String sql = SELECT_COLUMNS + """
			WHERE EXISTS (
				SELECT 1
				FROM students s
				JOIN teacher_groups tg ON tg.group_id = s.group_id
				JOIN teachers t ON t.id = tg.teacher_id
				WHERE s.id = absences.student_id AND t.user_id = ?
			)
			ORDER BY absence_date DESC, id DESC
			""";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, userId);
			try (ResultSet resultSet = statement.executeQuery()) {
				List<AbsenceResponse> absences = new ArrayList<>();
				while (resultSet.next()) {
					absences.add(mapAbsence(resultSet));
				}
				return absences;
			}
		}
	}

	public Optional<AbsenceResponse> findById(long id) throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			return findById(connection, id);
		}
	}

	public AbsenceResponse create(AbsenceRequest absence) throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			connection.setAutoCommit(false);
			try {
				requireReferences(connection, absence);
				if (existsDuplicate(connection, absence, null)) {
					throw new DuplicateAbsenceException();
				}

				String sql = """
					INSERT INTO absences (
						student_id, teacher_id, absence_date, subject, teacher_name,
						session_label, session_type, start_time, end_time, status
					) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""";
				try (PreparedStatement statement = connection.prepareStatement(sql)) {
					bindAbsence(statement, absence);
					statement.executeUpdate();
				}

				long id;
				try (Statement statement = connection.createStatement();
					 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
					if (!resultSet.next()) {
						throw new SQLException("SQLite n'a pas retourné l'identifiant de la nouvelle absence.");
					}
					id = resultSet.getLong(1);
				}
				AbsenceResponse created = findById(connection, id)
					.orElseThrow(() -> new SQLException("L'absence créée est introuvable."));
				connection.commit();
				return created;
			} catch (SQLException | RuntimeException exception) {
				rollback(connection, exception);
				throw exception;
			}
		}
	}

	public Optional<AbsenceResponse> update(long id, AbsenceRequest absence) throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			connection.setAutoCommit(false);
			try {
				if (findById(connection, id).isEmpty()) {
					connection.rollback();
					return Optional.empty();
				}
				requireReferences(connection, absence);
				if (existsDuplicate(connection, absence, id)) {
					throw new DuplicateAbsenceException();
				}

				String sql = """
					UPDATE absences
					SET student_id = ?, teacher_id = ?, absence_date = ?, subject = ?, teacher_name = ?,
					    session_label = ?, session_type = ?, start_time = ?, end_time = ?, status = ?
					WHERE id = ?
					""";
				try (PreparedStatement statement = connection.prepareStatement(sql)) {
					bindAbsence(statement, absence);
					statement.setLong(11, id);
					statement.executeUpdate();
				}
				AbsenceResponse updated = findById(connection, id)
					.orElseThrow(() -> new SQLException("L'absence modifiée est introuvable."));
				connection.commit();
				return Optional.of(updated);
			} catch (SQLException | RuntimeException exception) {
				rollback(connection, exception);
				throw exception;
			}
		}
	}

	public boolean delete(long id) throws SQLException {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement("DELETE FROM absences WHERE id = ?")) {
			statement.setLong(1, id);
			return statement.executeUpdate() > 0;
		}
	}

	public boolean studentExists(long studentId) throws SQLException {
		return referenceExists("students", studentId);
	}

	private boolean referenceExists(String table, long id) throws SQLException {
		String sql = "SELECT 1 FROM " + table + " WHERE id = ?";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, id);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private void requireReferences(Connection connection, AbsenceRequest absence) throws SQLException {
		if (!existsById(connection, "students", absence.studentId())) {
			throw new InvalidAbsenceReferenceException("L'étudiant référencé n'existe pas.");
		}
		if (absence.teacherId() != null && !existsById(connection, "teachers", absence.teacherId())) {
			throw new InvalidAbsenceReferenceException("L'enseignant référencé n'existe pas.");
		}
	}

	private boolean existsById(Connection connection, String table, long id) throws SQLException {
		String sql = "SELECT 1 FROM " + table + " WHERE id = ?";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, id);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private boolean existsDuplicate(
			Connection connection,
			AbsenceRequest absence,
			Long excludedId) throws SQLException {
		String sql = """
			SELECT 1
			FROM absences
			WHERE student_id = ?
			  AND absence_date = ?
			  AND lower(trim(subject)) = lower(trim(?))
			  AND session_type IS ?
			  AND (? IS NULL OR id <> ?)
			LIMIT 1
			""";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, absence.studentId());
			statement.setString(2, absence.absenceDate());
			statement.setString(3, absence.subject());
			statement.setString(4, absence.sessionType());
			if (excludedId == null) {
				statement.setNull(5, Types.INTEGER);
				statement.setNull(6, Types.INTEGER);
			} else {
				statement.setLong(5, excludedId);
				statement.setLong(6, excludedId);
			}
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private Optional<AbsenceResponse> findById(Connection connection, long id) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS + " WHERE id = ?")) {
			statement.setLong(1, id);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() ? Optional.of(mapAbsence(resultSet)) : Optional.empty();
			}
		}
	}

	private void bindAbsence(PreparedStatement statement, AbsenceRequest absence) throws SQLException {
		statement.setLong(1, absence.studentId());
		if (absence.teacherId() == null) {
			statement.setNull(2, Types.INTEGER);
		} else {
			statement.setLong(2, absence.teacherId());
		}
		statement.setString(3, absence.absenceDate());
		statement.setString(4, absence.subject());
		statement.setString(5, absence.teacherName());
		statement.setString(6, absence.sessionLabel());
		statement.setString(7, absence.sessionType());
		statement.setString(8, absence.startTime());
		statement.setString(9, absence.endTime());
		statement.setString(10, absence.status());
	}

	private AbsenceResponse mapAbsence(ResultSet resultSet) throws SQLException {
		long teacherIdValue = resultSet.getLong("teacher_id");
		Long teacherId = resultSet.wasNull() ? null : teacherIdValue;
		return new AbsenceResponse(
			resultSet.getLong("id"),
			resultSet.getLong("student_id"),
			teacherId,
			resultSet.getString("absence_date"),
			resultSet.getString("subject"),
			resultSet.getString("teacher_name"),
			resultSet.getString("session_label"),
			resultSet.getString("session_type"),
			resultSet.getString("start_time"),
			resultSet.getString("end_time"),
			resultSet.getString("status"),
			resultSet.getString("created_at")
		);
	}

	private void rollback(Connection connection, Exception exception) {
		try {
			connection.rollback();
		} catch (SQLException rollbackException) {
			exception.addSuppressed(rollbackException);
		}
	}

	public static class DuplicateAbsenceException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	public static class InvalidAbsenceReferenceException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public InvalidAbsenceReferenceException(String message) {
			super(message);
		}
	}
}
