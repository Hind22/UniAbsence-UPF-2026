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
import com.uniabsence.backend.dto.TeacherRequest;
import com.uniabsence.backend.dto.TeacherResponse;

@Repository
public class TeacherRepository {

	private static final String SELECT_COLUMNS = """
		SELECT id, user_id, teacher_identifier, first_name, last_name, email,
		       subject, grade, department, avatar, academic_year, created_at
		FROM teachers
		""";

	private final DatabaseConfig databaseConfig;

	public TeacherRepository(DatabaseConfig databaseConfig) {
		this.databaseConfig = databaseConfig;
	}

	public List<TeacherResponse> findAll() throws SQLException {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(
					SELECT_COLUMNS + " ORDER BY last_name COLLATE NOCASE, first_name COLLATE NOCASE, id");
			 ResultSet resultSet = statement.executeQuery()) {
			List<TeacherResponse> teachers = new ArrayList<>();
			while (resultSet.next()) {
				teachers.add(mapTeacher(connection, resultSet));
			}
			return teachers;
		}
	}

	public Optional<TeacherResponse> findById(long id) throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			return findById(connection, id);
		}
	}

	public TeacherResponse create(TeacherRequest teacher) throws SQLException {
		String sql = """
			INSERT INTO teachers (
				user_id, teacher_identifier, first_name, last_name, email, subject,
				grade, department, avatar, academic_year
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";
		try (Connection connection = databaseConfig.openConnection()) {
			connection.setAutoCommit(false);
			try {
				try (PreparedStatement statement = connection.prepareStatement(sql)) {
					bindTeacher(statement, teacher);
					statement.executeUpdate();
				}
				long id;
				try (Statement statement = connection.createStatement();
					 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
					if (!resultSet.next()) {
						throw new SQLException("SQLite n'a pas retourné l'identifiant du nouvel enseignant.");
					}
					id = resultSet.getLong(1);
				}
				replaceGroups(connection, id, teacher.groupIds());
				TeacherResponse created = findById(connection, id)
					.orElseThrow(() -> new SQLException("L'enseignant créé est introuvable."));
				connection.commit();
				return created;
			} catch (SQLException | RuntimeException exception) {
				rollback(connection, exception);
				throw exception;
			}
		}
	}

	public Optional<TeacherResponse> update(long id, TeacherRequest teacher) throws SQLException {
		String sql = """
			UPDATE teachers
			SET user_id = ?, teacher_identifier = ?, first_name = ?, last_name = ?, email = ?,
			    subject = ?, grade = ?, department = ?, avatar = ?, academic_year = ?
			WHERE id = ?
			""";
		try (Connection connection = databaseConfig.openConnection()) {
			connection.setAutoCommit(false);
			try {
				int updated;
				try (PreparedStatement statement = connection.prepareStatement(sql)) {
					bindTeacher(statement, teacher);
					statement.setLong(11, id);
					updated = statement.executeUpdate();
				}
				if (updated == 0) {
					connection.rollback();
					return Optional.empty();
				}
				replaceGroups(connection, id, teacher.groupIds());
				TeacherResponse result = findById(connection, id)
					.orElseThrow(() -> new SQLException("L'enseignant modifié est introuvable."));
				connection.commit();
				return Optional.of(result);
			} catch (SQLException | RuntimeException exception) {
				rollback(connection, exception);
				throw exception;
			}
		}
	}

	public boolean delete(long id) throws SQLException {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement("DELETE FROM teachers WHERE id = ?")) {
			statement.setLong(1, id);
			return statement.executeUpdate() > 0;
		}
	}

	private Optional<TeacherResponse> findById(Connection connection, long id) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS + " WHERE id = ?")) {
			statement.setLong(1, id);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next()
					? Optional.of(mapTeacher(connection, resultSet))
					: Optional.empty();
			}
		}
	}

	private TeacherResponse mapTeacher(Connection connection, ResultSet resultSet) throws SQLException {
		long userIdValue = resultSet.getLong("user_id");
		Long userId = resultSet.wasNull() ? null : userIdValue;
		long id = resultSet.getLong("id");
		return new TeacherResponse(
			id,
			userId,
			resultSet.getString("teacher_identifier"),
			resultSet.getString("first_name"),
			resultSet.getString("last_name"),
			resultSet.getString("email"),
			resultSet.getString("subject"),
			resultSet.getString("grade"),
			resultSet.getString("department"),
			resultSet.getString("avatar"),
			resultSet.getString("academic_year"),
			findGroupIds(connection, id),
			resultSet.getString("created_at")
		);
	}

	private List<String> findGroupIds(Connection connection, long teacherId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT group_id FROM teacher_groups WHERE teacher_id = ? ORDER BY group_id")) {
			statement.setLong(1, teacherId);
			try (ResultSet resultSet = statement.executeQuery()) {
				List<String> groupIds = new ArrayList<>();
				while (resultSet.next()) {
					groupIds.add(resultSet.getString("group_id"));
				}
				return groupIds;
			}
		}
	}

	private void replaceGroups(Connection connection, long teacherId, List<String> groupIds) throws SQLException {
		try (PreparedStatement delete = connection.prepareStatement(
				"DELETE FROM teacher_groups WHERE teacher_id = ?")) {
			delete.setLong(1, teacherId);
			delete.executeUpdate();
		}
		try (PreparedStatement insert = connection.prepareStatement(
				"INSERT INTO teacher_groups (teacher_id, group_id) VALUES (?, ?)")) {
			for (String groupId : groupIds) {
				insert.setLong(1, teacherId);
				insert.setString(2, groupId);
				insert.addBatch();
			}
			insert.executeBatch();
		}
	}

	private void bindTeacher(PreparedStatement statement, TeacherRequest teacher) throws SQLException {
		if (teacher.userId() == null) {
			statement.setNull(1, Types.INTEGER);
		} else {
			statement.setLong(1, teacher.userId());
		}
		statement.setString(2, teacher.teacherIdentifier());
		statement.setString(3, teacher.firstName());
		statement.setString(4, teacher.lastName());
		statement.setString(5, teacher.email());
		statement.setString(6, teacher.subject());
		statement.setString(7, teacher.grade());
		statement.setString(8, teacher.department());
		statement.setString(9, teacher.avatar());
		statement.setString(10, teacher.academicYear());
	}

	private void rollback(Connection connection, Exception exception) {
		try {
			connection.rollback();
		} catch (SQLException rollbackException) {
			exception.addSuppressed(rollbackException);
		}
	}
}
