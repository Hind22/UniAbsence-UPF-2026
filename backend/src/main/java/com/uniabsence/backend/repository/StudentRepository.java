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
import com.uniabsence.backend.dto.StudentRequest;
import com.uniabsence.backend.dto.StudentResponse;

@Repository
public class StudentRepository {

	private static final String SELECT_COLUMNS = """
		SELECT id, user_id, student_number, first_name, last_name, email,
		       group_id, program, avatar, academic_year, created_at
		FROM students
		""";

	private final DatabaseConfig databaseConfig;

	public StudentRepository(DatabaseConfig databaseConfig) {
		this.databaseConfig = databaseConfig;
	}

	public List<StudentResponse> findAll() throws SQLException {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(
					SELECT_COLUMNS + " ORDER BY last_name COLLATE NOCASE, first_name COLLATE NOCASE, id");
			 ResultSet resultSet = statement.executeQuery()) {
			List<StudentResponse> students = new ArrayList<>();
			while (resultSet.next()) {
				students.add(mapStudent(resultSet));
			}
			return students;
		}
	}

	public List<StudentResponse> findForTeacherUserId(long teacherUserId) throws SQLException {
		String sql = SELECT_COLUMNS + """
			WHERE EXISTS (
				SELECT 1
				FROM teacher_groups tg
				JOIN teachers t ON t.id = tg.teacher_id
				WHERE tg.group_id = students.group_id AND t.user_id = ?
			)
			ORDER BY last_name COLLATE NOCASE, first_name COLLATE NOCASE, id
			""";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, teacherUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				List<StudentResponse> students = new ArrayList<>();
				while (resultSet.next()) {
					students.add(mapStudent(resultSet));
				}
				return students;
			}
		}
	}

	public Optional<StudentResponse> findById(long id) throws SQLException {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS + " WHERE id = ?")) {
			statement.setLong(1, id);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() ? Optional.of(mapStudent(resultSet)) : Optional.empty();
			}
		}
	}

	public StudentResponse create(StudentRequest student) throws SQLException {
		String sql = """
			INSERT INTO students (
				user_id, student_number, first_name, last_name, email,
				group_id, program, avatar, academic_year
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			bindStudent(statement, student);
			statement.executeUpdate();
			try (Statement idStatement = connection.createStatement();
				 ResultSet resultSet = idStatement.executeQuery("SELECT last_insert_rowid()")) {
				if (!resultSet.next()) {
					throw new SQLException("SQLite n'a pas retourné l'identifiant du nouvel étudiant.");
				}
				return findById(connection, resultSet.getLong(1))
					.orElseThrow(() -> new SQLException("L'étudiant créé est introuvable."));
			}
		}
	}

	public Optional<StudentResponse> update(long id, StudentRequest student) throws SQLException {
		String sql = """
			UPDATE students
			SET user_id = ?, student_number = ?, first_name = ?, last_name = ?, email = ?,
			    group_id = ?, program = ?, avatar = ?, academic_year = ?
			WHERE id = ?
			""";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			bindStudent(statement, student);
			statement.setLong(10, id);
			if (statement.executeUpdate() == 0) {
				return Optional.empty();
			}
			return findById(connection, id);
		}
	}

	public boolean delete(long id) throws SQLException {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement("DELETE FROM students WHERE id = ?")) {
			statement.setLong(1, id);
			return statement.executeUpdate() > 0;
		}
	}

	private Optional<StudentResponse> findById(Connection connection, long id) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS + " WHERE id = ?")) {
			statement.setLong(1, id);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() ? Optional.of(mapStudent(resultSet)) : Optional.empty();
			}
		}
	}

	private void bindStudent(PreparedStatement statement, StudentRequest student) throws SQLException {
		if (student.userId() == null) {
			statement.setNull(1, Types.INTEGER);
		} else {
			statement.setLong(1, student.userId());
		}
		statement.setString(2, student.studentNumber());
		statement.setString(3, student.firstName());
		statement.setString(4, student.lastName());
		statement.setString(5, student.email());
		statement.setString(6, student.groupId());
		statement.setString(7, student.program());
		statement.setString(8, student.avatar());
		statement.setString(9, student.academicYear());
	}

	private StudentResponse mapStudent(ResultSet resultSet) throws SQLException {
		long userIdValue = resultSet.getLong("user_id");
		Long userId = resultSet.wasNull() ? null : userIdValue;
		return new StudentResponse(
			resultSet.getLong("id"),
			userId,
			resultSet.getString("student_number"),
			resultSet.getString("first_name"),
			resultSet.getString("last_name"),
			resultSet.getString("email"),
			resultSet.getString("group_id"),
			resultSet.getString("program"),
			resultSet.getString("avatar"),
			resultSet.getString("academic_year"),
			resultSet.getString("created_at")
		);
	}
}
