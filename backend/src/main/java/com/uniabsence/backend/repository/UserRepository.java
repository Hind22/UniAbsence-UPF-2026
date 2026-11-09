package com.uniabsence.backend.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.uniabsence.backend.config.DatabaseConfig;
import com.uniabsence.backend.dto.UserAccountResponse;

@Repository
public class UserRepository {

	private final DatabaseConfig databaseConfig;

	public UserRepository(DatabaseConfig databaseConfig) {
		this.databaseConfig = databaseConfig;
	}

	public UserAccountResponse createLinkedAccount(String profileType, long profileId, String passwordHash)
			throws SQLException {
		Profile profile = findProfile(profileType, profileId)
			.orElseThrow(() -> new ProfileNotFoundException(profileType, profileId));
		if (profile.userId() != null) {
			throw new ProfileAlreadyHasAccountException();
		}

		try (Connection connection = databaseConfig.openConnection()) {
			connection.setAutoCommit(false);
			try {
				long userId;
				try (PreparedStatement statement = connection.prepareStatement(
						"INSERT INTO users (email, password_hash, role) VALUES (?, ?, ?)")) {
					statement.setString(1, profile.email());
					statement.setString(2, passwordHash);
					statement.setString(3, databaseRole(profileType));
					statement.executeUpdate();
				}
				try (Statement statement = connection.createStatement();
					 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
					if (!resultSet.next()) {
						throw new SQLException("SQLite n'a pas retourné l'identifiant du compte créé.");
					}
					userId = resultSet.getLong(1);
				}
				String table = profileTable(profileType);
				try (PreparedStatement statement = connection.prepareStatement(
						"UPDATE " + table + " SET user_id = ? WHERE id = ? AND user_id IS NULL")) {
					statement.setLong(1, userId);
					statement.setLong(2, profileId);
					if (statement.executeUpdate() != 1) {
						throw new ProfileAlreadyHasAccountException();
					}
				}
				UserAccountResponse response = new UserAccountResponse(
					userId,
					profile.email(),
					apiRole(databaseRole(profileType)),
					profileType,
					profileId,
					profile.name()
				);
				connection.commit();
				return response;
			} catch (SQLException | RuntimeException exception) {
				rollback(connection, exception);
				throw exception;
			}
		}
	}

	public List<UserAccountResponse> findStudentAndTeacherAccounts() throws SQLException {
		String sql = """
			SELECT u.id, u.email, u.role, s.id AS profile_id,
			       s.first_name, s.last_name, 'STUDENT' AS profile_type
			FROM users u JOIN students s ON s.user_id = u.id
			WHERE u.role = 'student'
			UNION ALL
			SELECT u.id, u.email, u.role, t.id AS profile_id,
			       t.first_name, t.last_name, 'TEACHER' AS profile_type
			FROM users u JOIN teachers t ON t.user_id = u.id
			WHERE u.role = 'teacher'
			ORDER BY 2 COLLATE NOCASE
			""";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql);
			 ResultSet resultSet = statement.executeQuery()) {
			List<UserAccountResponse> accounts = new ArrayList<>();
			while (resultSet.next()) {
				accounts.add(mapAccount(resultSet));
			}
			return accounts;
		}
	}

	public Optional<UserAccountResponse> updateLinkedAccount(
			long userId,
			String email,
			String passwordHash) throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			connection.setAutoCommit(false);
			try {
				AccountProfile account = findAccountProfile(connection, userId)
					.orElse(null);
				if (account == null) {
					connection.rollback();
					return Optional.empty();
				}
				if (!"student".equals(account.databaseRole()) && !"teacher".equals(account.databaseRole())) {
					throw new ProtectedAccountException();
				}
				if (account.profileId() == null) {
					throw new SQLException("Le compte ne possède aucun profil étudiant ou enseignant associé.");
				}
				String normalizedEmail = email == null ? account.email() : email;
				String sql = passwordHash == null
					? "UPDATE users SET email = ? WHERE id = ?"
					: "UPDATE users SET email = ?, password_hash = ? WHERE id = ?";
				try (PreparedStatement statement = connection.prepareStatement(sql)) {
					statement.setString(1, normalizedEmail);
					if (passwordHash == null) {
						statement.setLong(2, userId);
					} else {
						statement.setString(2, passwordHash);
						statement.setLong(3, userId);
					}
					statement.executeUpdate();
				}
				if (email != null) {
					String table = "student".equals(account.databaseRole()) ? "students" : "teachers";
					try (PreparedStatement statement = connection.prepareStatement(
							"UPDATE " + table + " SET email = ? WHERE id = ? AND user_id = ?")) {
						statement.setString(1, normalizedEmail);
						statement.setLong(2, account.profileId());
						statement.setLong(3, userId);
						if (statement.executeUpdate() != 1) {
							throw new SQLException("Le profil associé au compte n'a pas pu être mis à jour.");
						}
					}
				}
				UserAccountResponse result = new UserAccountResponse(
					userId,
					normalizedEmail,
					"student".equals(account.databaseRole()) ? "STUDENT" : "TEACHER",
					"student".equals(account.databaseRole()) ? "STUDENT" : "TEACHER",
					account.profileId(),
					account.name()
				);
				connection.commit();
				return Optional.of(result);
			} catch (SQLException | RuntimeException exception) {
				rollback(connection, exception);
				throw exception;
			}
		}
	}

	private Optional<Profile> findProfile(String profileType, long profileId) throws SQLException {
		String table = profileTable(profileType);
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(
					"SELECT user_id, email, first_name, last_name FROM " + table + " WHERE id = ?")) {
			statement.setLong(1, profileId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return Optional.empty();
				}
				long userId = resultSet.getLong("user_id");
				return Optional.of(new Profile(
					resultSet.wasNull() ? null : userId,
					resultSet.getString("email"),
					(resultSet.getString("first_name") + " " + resultSet.getString("last_name")).trim()
				));
			}
		}
	}

	private Optional<AccountProfile> findAccountProfile(Connection connection, long userId) throws SQLException {
		String sql = """
			SELECT u.email, u.role,
			       COALESCE(s.id, t.id) AS profile_id,
			       COALESCE(s.first_name, t.first_name) AS first_name,
			       COALESCE(s.last_name, t.last_name) AS last_name
			FROM users u
			LEFT JOIN students s ON s.user_id = u.id AND u.role = 'student'
			LEFT JOIN teachers t ON t.user_id = u.id AND u.role = 'teacher'
			WHERE u.id = ?
			""";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, userId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return Optional.empty();
				}
				long profileId = resultSet.getLong("profile_id");
				boolean hasProfile = !resultSet.wasNull();
				String email = resultSet.getString("email");
				String role = resultSet.getString("role");
				String firstName = resultSet.getString("first_name");
				String lastName = resultSet.getString("last_name");
				return Optional.of(new AccountProfile(
					email,
					role,
					hasProfile ? profileId : null,
					(firstName + " " + lastName).trim()
				));
			}
		}
	}

	private UserAccountResponse mapAccount(ResultSet resultSet) throws SQLException {
		return new UserAccountResponse(
			resultSet.getLong("id"),
			resultSet.getString("email"),
			apiRole(resultSet.getString("role")),
			resultSet.getString("profile_type"),
			resultSet.getLong("profile_id"),
			(resultSet.getString("first_name") + " " + resultSet.getString("last_name")).trim()
		);
	}

	private String profileTable(String profileType) {
		return switch (profileType) {
			case "STUDENT" -> "students";
			case "TEACHER" -> "teachers";
			default -> throw new IllegalArgumentException("Type de profil non autorisé.");
		};
	}

	private String databaseRole(String profileType) {
		return profileType.toLowerCase(Locale.ROOT);
	}

	private String apiRole(String databaseRole) {
		return switch (databaseRole) {
			case "student" -> "STUDENT";
			case "teacher" -> "TEACHER";
			default -> throw new IllegalStateException("Rôle utilisateur inconnu.");
		};
	}

	private void rollback(Connection connection, Exception exception) {
		try {
			connection.rollback();
		} catch (SQLException rollbackException) {
			exception.addSuppressed(rollbackException);
		}
	}

	public static final class ProfileNotFoundException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public ProfileNotFoundException(String profileType, long profileId) {
			super("Profil " + profileType + " " + profileId + " introuvable.");
		}
	}

	public static final class ProfileAlreadyHasAccountException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public ProfileAlreadyHasAccountException() {
			super("Ce profil possède déjà un compte de connexion.");
		}
	}

	public static final class ProtectedAccountException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public ProtectedAccountException() {
			super("Les comptes administrateur ne peuvent pas être modifiés par cette opération.");
		}
	}

	private record Profile(Long userId, String email, String name) {
	}

	private record AccountProfile(String email, String databaseRole, Long profileId, String name) {
	}
}
