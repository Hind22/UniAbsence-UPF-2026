package com.uniabsence.backend.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.uniabsence.backend.config.DatabaseConfig;

@Repository
public class AuthRepository {

	private static final String ACCOUNT_QUERY = """
		SELECT u.id, u.email, u.password_hash, u.role,
		       s.id AS student_id, t.id AS teacher_id,
		       COALESCE(s.first_name, t.first_name) AS first_name,
		       COALESCE(s.last_name, t.last_name) AS last_name
		FROM users u
		LEFT JOIN students s ON s.user_id = u.id AND u.role = 'student'
		LEFT JOIN teachers t ON t.user_id = u.id AND u.role = 'teacher'
		WHERE u.email = ?
		""";

	private final DatabaseConfig databaseConfig;

	public AuthRepository(DatabaseConfig databaseConfig) {
		this.databaseConfig = databaseConfig;
	}

	public Optional<Account> findByEmail(String email) throws SQLException {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(ACCOUNT_QUERY)) {
			statement.setString(1, email);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return Optional.empty();
				}
				return Optional.of(new Account(
					resultSet.getLong("id"),
					resultSet.getString("email"),
					resultSet.getString("password_hash"),
					resultSet.getString("role"),
					nullableLong(resultSet, "student_id"),
					nullableLong(resultSet, "teacher_id"),
					resultSet.getString("first_name"),
					resultSet.getString("last_name")
				));
			}
		}
	}

	private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
		long value = resultSet.getLong(column);
		return resultSet.wasNull() ? null : value;
	}

	public record Account(
		long id,
		String email,
		String passwordHash,
		String databaseRole,
		Long studentId,
		Long teacherId,
		String firstName,
		String lastName
	) {
	}
}
