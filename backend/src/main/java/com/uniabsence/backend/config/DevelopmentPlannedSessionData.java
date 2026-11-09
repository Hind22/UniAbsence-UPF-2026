package com.uniabsence.backend.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@Order(5)
public class DevelopmentPlannedSessionData implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(DevelopmentPlannedSessionData.class);
	private static final String DEMO_ACADEMIC_YEAR = "2026-2027";

	private record PlannedSeed(
		String date,
		String startTime,
		String endTime,
		String subject,
		String sessionType,
		String room
	) {}

	private static final List<PlannedSeed> STANDARD_OCTOBER_SESSIONS = List.of(
		new PlannedSeed("2026-10-05", "08:00", "10:00", "Algorithmique avancée", "CM", "Amphi A"),
		new PlannedSeed("2026-10-07", "10:00", "12:00", "Développement mobile", "TD", "Salle démo 02"),
		new PlannedSeed("2026-10-10", "14:00", "16:00", "Développement mobile", "TP", "Labo démo 01"),
		new PlannedSeed("2026-10-12", "08:00", "10:00", "Développement mobile", "CM", "Salle démo 01"),
		new PlannedSeed("2026-10-14", "10:00", "12:00", "Développement mobile", "TD", "Salle démo 02"),
		new PlannedSeed("2026-10-17", "14:00", "16:00", "Développement mobile", "TP", "Labo démo 01"),
		new PlannedSeed("2026-10-19", "08:00", "10:00", "Développement mobile", "CM", "Salle démo 01"),
		new PlannedSeed("2026-10-21", "10:00", "12:00", "Développement mobile", "TD", "Salle démo 02"),
		new PlannedSeed("2026-10-24", "14:00", "16:00", "Développement mobile", "TP", "Labo démo 01"),
		new PlannedSeed("2026-10-26", "08:00", "10:00", "Développement mobile", "CM", "Salle démo 01"),
		new PlannedSeed("2026-10-28", "10:00", "12:00", "Développement mobile", "TD", "Salle démo 02"),
		new PlannedSeed("2026-10-31", "14:00", "16:00", "Développement mobile", "TP", "Labo démo 01"),
		new PlannedSeed("2026-11-04", "10:00", "12:00", "Développement mobile", "TD", "Salle démo 02"),
		new PlannedSeed("2026-11-07", "14:00", "16:00", "Développement mobile", "TP", "Labo démo 01"),
		new PlannedSeed("2026-11-09", "08:00", "10:00", "Développement mobile", "CM", "Salle démo 01")
	);

	private final DatabaseConfig databaseConfig;

	public DevelopmentPlannedSessionData(DatabaseConfig databaseConfig) {
		this.databaseConfig = databaseConfig;
	}

	@Override
	public void run(ApplicationArguments args) throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			connection.setAutoCommit(false);
			try {
				createTableIfNotExists(connection);
				seedPlannedSessionsForGroups(connection);
				connection.commit();
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

	private void createTableIfNotExists(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
				CREATE TABLE IF NOT EXISTS planned_sessions (
					id INTEGER PRIMARY KEY AUTOINCREMENT,
					group_id TEXT NOT NULL,
					academic_year TEXT NOT NULL,
					session_date TEXT NOT NULL,
					start_time TEXT NOT NULL,
					end_time TEXT NOT NULL,
					subject TEXT NOT NULL,
					session_type TEXT NOT NULL CHECK (session_type IN ('CM', 'TD', 'TP')),
					teacher_id INTEGER,
					room TEXT,
					status TEXT NOT NULL DEFAULT 'scheduled'
						CHECK (status IN ('scheduled', 'completed', 'cancelled')),
					created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
					FOREIGN KEY (group_id) REFERENCES groups(id) ON UPDATE CASCADE ON DELETE CASCADE,
					FOREIGN KEY (teacher_id) REFERENCES teachers(id) ON DELETE SET NULL,
					UNIQUE (group_id, academic_year, session_date, start_time, session_type)
				)
				""");
		}
	}

	private void seedPlannedSessionsForGroups(Connection connection) throws SQLException {
		try (PreparedStatement groups = connection.prepareStatement("SELECT id, academic_year FROM groups ORDER BY id");
			 ResultSet groupRows = groups.executeQuery()) {
			while (groupRows.next()) {
				String groupId = groupRows.getString("id");
				String academicYear = groupRows.getString("academic_year");
				if (academicYear == null || academicYear.isBlank()) {
					academicYear = DEMO_ACADEMIC_YEAR;
				}

				if (hasPlannedSessions(connection, groupId, academicYear)) {
					continue;
				}

				Long teacherId = findAssignedTeacherId(connection, groupId);
				seedGroup(connection, groupId, academicYear, teacherId);
			}
		}
	}

	private boolean hasPlannedSessions(Connection connection, String groupId, String academicYear) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT 1 FROM planned_sessions WHERE group_id = ? AND academic_year = ? LIMIT 1")) {
			statement.setString(1, groupId);
			statement.setString(2, academicYear);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private Long findAssignedTeacherId(Connection connection, String groupId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT tg.teacher_id
				FROM teacher_groups tg
				WHERE tg.group_id = ?
				ORDER BY tg.teacher_id
				LIMIT 1
				""")) {
			statement.setString(1, groupId);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() ? resultSet.getLong("teacher_id") : null;
			}
		}
	}

	private void seedGroup(
			Connection connection,
			String groupId,
			String academicYear,
			Long teacherId) throws SQLException {

		// 1. Recover existing absence sessions for students in this group so they align with real scheduled sessions
		List<PlannedSeed> seeds = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT DISTINCT a.absence_date, a.start_time, a.end_time, a.subject, a.session_type
				FROM absences a
				JOIN students s ON s.id = a.student_id
				WHERE s.group_id = ? AND a.absence_date BETWEEN '2026-10-01' AND '2026-11-30'
				ORDER BY a.absence_date, a.start_time
				""")) {
			statement.setString(1, groupId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					String date = resultSet.getString("absence_date");
					String start = resultSet.getString("start_time");
					String end = resultSet.getString("end_time");
					String subject = resultSet.getString("subject");
					String type = resultSet.getString("session_type");
					if (start == null || start.isBlank()) start = "08:00";
					if (end == null || end.isBlank()) end = "10:00";
					if (type == null || type.isBlank()) type = "CM";
					if (subject == null || subject.isBlank()) subject = "Cours";
					seeds.add(new PlannedSeed(date, start, end, subject, type, "Salle démo 01"));
				}
			}
		}

		// 2. Add standard sessions across the semester to reach 20 distinct planned sessions
		for (PlannedSeed standard : STANDARD_OCTOBER_SESSIONS) {
			boolean duplicate = seeds.stream().anyMatch(s ->
				s.date().equals(standard.date()) && s.startTime().equals(standard.startTime()) && s.sessionType().equals(standard.sessionType()));
			if (!duplicate) {
				seeds.add(standard);
			}
			if (seeds.size() >= 20) {
				break;
			}
		}

		// 3. Insert into planned_sessions
		String insertSql = """
			INSERT INTO planned_sessions (
				group_id, academic_year, session_date, start_time, end_time,
				subject, session_type, teacher_id, room, status
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'scheduled')
			ON CONFLICT(group_id, academic_year, session_date, start_time, session_type) DO NOTHING
			""";
		try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
			for (PlannedSeed seed : seeds) {
				statement.setString(1, groupId);
				statement.setString(2, academicYear);
				statement.setString(3, seed.date());
				statement.setString(4, seed.startTime());
				statement.setString(5, seed.endTime());
				statement.setString(6, seed.subject());
				statement.setString(7, seed.sessionType());
				if (teacherId == null) {
					statement.setNull(8, java.sql.Types.INTEGER);
				} else {
					statement.setLong(8, teacherId);
				}
				statement.setString(9, seed.room());
				statement.addBatch();
			}
			statement.executeBatch();
			logger.info("Séances prévues initialisées pour le groupe {} ({} séances).", groupId, seeds.size());
		}
	}
}
