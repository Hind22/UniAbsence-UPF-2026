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
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@Order(4)
public class DevelopmentTimetableData implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(DevelopmentTimetableData.class);
	private static final String DEMO_ACADEMIC_YEAR = "2026-2027";
	private static final List<DemoSession> DEMO_SESSIONS = List.of(
		new DemoSession(1, "08:00", "10:00", "CM", "Salle démo 01"),
		new DemoSession(3, "10:00", "12:00", "TD", "Salle démo 02"),
		new DemoSession(6, "14:00", "16:00", "TP", "Labo démo 01")
	);

	private final DatabaseConfig databaseConfig;

	public DevelopmentTimetableData(DatabaseConfig databaseConfig) {
		this.databaseConfig = databaseConfig;
	}

	@Override
	public void run(ApplicationArguments args) throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			connection.setAutoCommit(false);
			try {
				createTable(connection);
				seedEmptyGroups(connection);
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

	private void createTable(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
				CREATE TABLE IF NOT EXISTS timetable_sessions (
					id INTEGER PRIMARY KEY AUTOINCREMENT,
					group_id TEXT NOT NULL,
					academic_year TEXT NOT NULL,
					day_of_week INTEGER NOT NULL CHECK (day_of_week BETWEEN 1 AND 6),
					subject TEXT NOT NULL,
					start_time TEXT NOT NULL,
					end_time TEXT NOT NULL,
					session_type TEXT NOT NULL CHECK (session_type IN ('CM', 'TD', 'TP')),
					teacher_id INTEGER,
					room TEXT NOT NULL,
					is_demo INTEGER NOT NULL DEFAULT 0 CHECK (is_demo IN (0, 1)),
					created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
					FOREIGN KEY (group_id) REFERENCES groups(id) ON UPDATE CASCADE ON DELETE CASCADE,
					FOREIGN KEY (teacher_id) REFERENCES teachers(id) ON DELETE SET NULL,
					UNIQUE (group_id, academic_year, day_of_week, start_time, session_type)
				)
				""");
		}
	}

	private void seedEmptyGroups(Connection connection) throws SQLException {
		try (PreparedStatement groups = connection.prepareStatement(
				"SELECT id, main_subject, academic_year FROM groups ORDER BY id");
			 ResultSet groupRows = groups.executeQuery()) {
			while (groupRows.next()) {
				String groupId = groupRows.getString("id");
				String academicYear = groupRows.getString("academic_year");
				if (isBlank(academicYear)) {
					academicYear = DEMO_ACADEMIC_YEAR;
				}
				if (hasSchedule(connection, groupId, academicYear)) {
					continue;
				}

				Teacher teacher = findAssignedTeacher(connection, groupId);
				String groupSubject = groupRows.getString("main_subject");
				String subject = teacher != null && !isBlank(teacher.subject())
					? teacher.subject()
					: isBlank(groupSubject) ? "Cours de démonstration" : groupSubject;
				insertDemoSessions(
					connection,
					groupId,
					academicYear,
					subject,
					teacher == null ? null : teacher.id()
				);
			}
		}
	}

	private boolean hasSchedule(Connection connection, String groupId, String academicYear) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT 1 FROM timetable_sessions WHERE group_id = ? AND academic_year = ? LIMIT 1")) {
			statement.setString(1, groupId);
			statement.setString(2, academicYear);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private Teacher findAssignedTeacher(Connection connection, String groupId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT t.id, t.subject
				FROM teacher_groups tg
				JOIN teachers t ON t.id = tg.teacher_id
				WHERE tg.group_id = ?
				ORDER BY t.id
				LIMIT 1
				""")) {
			statement.setString(1, groupId);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next()
					? new Teacher(resultSet.getLong("id"), resultSet.getString("subject"))
					: null;
			}
		}
	}

	private void insertDemoSessions(
			Connection connection,
			String groupId,
			String academicYear,
			String subject,
			Long teacherId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO timetable_sessions (
					group_id, academic_year, day_of_week, subject, start_time, end_time,
					session_type, teacher_id, room, is_demo
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
				""")) {
			for (DemoSession session : DEMO_SESSIONS) {
				statement.setString(1, groupId);
				statement.setString(2, academicYear);
				statement.setInt(3, session.dayOfWeek());
				statement.setString(4, subject);
				statement.setString(5, session.startTime());
				statement.setString(6, session.endTime());
				statement.setString(7, session.sessionType());
				if (teacherId == null) {
					statement.setNull(8, java.sql.Types.INTEGER);
				} else {
					statement.setLong(8, teacherId);
				}
				statement.setString(9, session.room());
				statement.addBatch();
			}
			statement.executeBatch();
			logger.info("Séances de démonstration créées pour le groupe {} ({}).", groupId, DEMO_ACADEMIC_YEAR);
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private record DemoSession(
		int dayOfWeek,
		String startTime,
		String endTime,
		String sessionType,
		String room
	) {
	}

	private record Teacher(long id, String subject) {
	}
}
