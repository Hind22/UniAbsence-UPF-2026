package com.uniabsence.backend.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class DatabaseConfig {

	private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
	private static final String SQLITE_URL_PREFIX = "jdbc:sqlite:";

	@Value("${app.database.url}")
	private String databaseUrl;

	@Bean
	@Order(1)
	ApplicationRunner verifyDatabaseConnection() {
		return args -> initializeDatabase();
	}

	public Connection openConnection() throws SQLException {
		Connection connection = DriverManager.getConnection(databaseUrl);
		try {
			try (Statement statement = connection.createStatement()) {
				statement.execute("PRAGMA foreign_keys = ON");
			}
			return connection;
		} catch (SQLException exception) {
			try {
				connection.close();
			} catch (SQLException closeException) {
				exception.addSuppressed(closeException);
			}
			throw exception;
		}
	}

	private void initializeDatabase() throws IOException, SQLException {
		try {
			ensureDatabaseDirectoryExists();
			try (Connection connection = openConnection()) {
				if (!connection.isValid(2)) {
					throw new SQLException("La connexion SQLite a été ouverte mais n'est pas valide.");
				}
				DatabaseSchema.createTables(connection);
				logger.info("Connexion SQLite réussie et schéma initialisé : {}", databaseUrl);
			}
		} catch (IOException | SQLException | IllegalArgumentException exception) {
			logger.error("Échec de la connexion SQLite : {}", databaseUrl, exception);
			throw exception;
		}
	}

	private void ensureDatabaseDirectoryExists() throws IOException {
		if (!databaseUrl.startsWith(SQLITE_URL_PREFIX)) {
			throw new IllegalArgumentException("L'URL de base de données doit utiliser le pilote SQLite.");
		}

		String databasePathValue = databaseUrl.substring(SQLITE_URL_PREFIX.length());
		if (databasePathValue.isBlank() || ":memory:".equals(databasePathValue)) {
			return;
		}

		Path databasePath = Path.of(databasePathValue);
		Path parentDirectory = databasePath.toAbsolutePath().getParent();
		if (parentDirectory != null) {
			Files.createDirectories(parentDirectory);
		}
	}
}
