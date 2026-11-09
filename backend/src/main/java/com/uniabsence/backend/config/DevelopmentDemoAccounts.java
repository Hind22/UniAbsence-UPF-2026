package com.uniabsence.backend.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@Order(2)
public class DevelopmentDemoAccounts implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(DevelopmentDemoAccounts.class);
	private static final String DEMO_GROUP_ID = "DEMO-L3-INFO";

	private static final List<DemoAccount> ACCOUNTS = List.of(
		new DemoAccount("demo-student@uniabsence.dz", "StudentDemo2026!", "student", "student"),
		new DemoAccount("m.kaddour@univ-alger.dz", "TeacherDemo2026!", "teacher", "teacher"),
		new DemoAccount("scolarite@univ-alger.dz", "AdminDemo2026!", "administrator", "administrator")
	);

	private final DatabaseConfig databaseConfig;
	private final PasswordEncoder passwordEncoder;

	public DevelopmentDemoAccounts(DatabaseConfig databaseConfig, PasswordEncoder passwordEncoder) {
		this.databaseConfig = databaseConfig;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(ApplicationArguments args) throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			connection.setAutoCommit(false);
			try {
				ensureDemoGroup(connection);
				for (DemoAccount account : ACCOUNTS) {
					ensureAccount(connection, account);
				}
				connection.commit();
				logger.info("Les comptes de démonstration sont disponibles (profil dev uniquement).");
			} catch (SQLException | RuntimeException exception) {
				try {
					connection.rollback();
				} catch (SQLException rollbackException) {
					exception.addSuppressed(rollbackException);
				}
				throw exception;
			}
		}
	}

	private void ensureAccount(Connection connection, DemoAccount account) throws SQLException {
		String insertSql = """
			INSERT INTO users (email, password_hash, role)
			VALUES (?, ?, ?)
			ON CONFLICT(email) DO NOTHING
			""";
		try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
			statement.setString(1, account.email());
			statement.setString(2, passwordEncoder.encode(account.password()));
			statement.setString(3, account.role());
			statement.executeUpdate();
		}

		long userId;
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT id, role FROM users WHERE email = ?")) {
			statement.setString(1, account.email());
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next() || !account.role().equals(resultSet.getString("role"))) {
					return;
				}
				userId = resultSet.getLong("id");
			}
		}

		if ("student".equals(account.profileType())) {
			ensureStudentProfile(connection, userId, account.email());
		} else if ("teacher".equals(account.profileType())) {
			ensureTeacherProfile(connection, userId, account.email());
		}
	}

	private void ensureDemoGroup(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO groups (id, name, specialty, main_subject, academic_year)
				VALUES (?, ?, ?, ?, ?)
				ON CONFLICT(id) DO NOTHING
				""")) {
			statement.setString(1, DEMO_GROUP_ID);
			statement.setString(2, "Groupe démo L3 Informatique");
			statement.setString(3, "Informatique");
			statement.setString(4, "Développement mobile");
			statement.setString(5, "2026-2027");
			statement.executeUpdate();
		}
	}

	private void ensureStudentProfile(Connection connection, long userId, String email) throws SQLException {
		if (findProfileId(connection, "students", "user_id", userId) != null) {
			return;
		}
		Long existingProfileId = findProfileIdByEmail(connection, "students", email);
		if (existingProfileId != null) {
			linkUnassignedProfile(connection, "students", existingProfileId, userId);
			return;
		}

		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO students (
					user_id, student_number, first_name, last_name, email,
					group_id, program, academic_year
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""")) {
			statement.setLong(1, userId);
			statement.setString(2, "DEMO-STUDENT-001");
			statement.setString(3, "Ahmed");
			statement.setString(4, "Benali");
			statement.setString(5, email);
			statement.setString(6, DEMO_GROUP_ID);
			statement.setString(7, "Informatique – L3");
			statement.setString(8, "2026-2027");
			statement.executeUpdate();
		}
	}

	private void ensureTeacherProfile(Connection connection, long userId, String email) throws SQLException {
		if (findProfileId(connection, "teachers", "user_id", userId) != null) {
			return;
		}
		Long existingProfileId = findProfileIdByEmail(connection, "teachers", email);
		if (existingProfileId != null) {
			linkUnassignedProfile(connection, "teachers", existingProfileId, userId);
			return;
		}

		long teacherId;
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO teachers (
					user_id, teacher_identifier, first_name, last_name, email,
					subject, grade, department, academic_year
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")) {
			statement.setLong(1, userId);
			statement.setString(2, "DEMO-TEACHER-001");
			statement.setString(3, "Mohamed");
			statement.setString(4, "Kaddour");
			statement.setString(5, email);
			statement.setString(6, "Développement mobile");
			statement.setString(7, "Enseignant");
			statement.setString(8, "Informatique");
			statement.setString(9, "2026-2027");
			statement.executeUpdate();
			try (Statement idStatement = connection.createStatement();
				 ResultSet resultSet = idStatement.executeQuery("SELECT last_insert_rowid()")) {
				if (!resultSet.next()) {
					throw new SQLException("SQLite n'a pas retourné l'identifiant du profil enseignant.");
				}
				teacherId = resultSet.getLong(1);
			}
		}

		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO teacher_groups (teacher_id, group_id)
				VALUES (?, ?)
				ON CONFLICT(teacher_id, group_id) DO NOTHING
				""")) {
			statement.setLong(1, teacherId);
			statement.setString(2, DEMO_GROUP_ID);
			statement.executeUpdate();
		}
	}

	private Long findProfileId(
			Connection connection,
			String table,
			String column,
			long value) throws SQLException {
		String sql = "SELECT id FROM " + table + " WHERE " + column + " = ?";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, value);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() ? resultSet.getLong("id") : null;
			}
		}
	}

	private Long findProfileIdByEmail(Connection connection, String table, String email) throws SQLException {
		String sql = "SELECT id FROM " + table + " WHERE email = ?";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, email);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() ? resultSet.getLong("id") : null;
			}
		}
	}

	private void linkUnassignedProfile(Connection connection, String table, long profileId, long userId)
			throws SQLException {
		String sql = "UPDATE " + table + " SET user_id = ? WHERE id = ? AND user_id IS NULL";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, userId);
			statement.setLong(2, profileId);
			statement.executeUpdate();
		}
	}

	private record DemoAccount(String email, String password, String role, String profileType) {
	}
}
