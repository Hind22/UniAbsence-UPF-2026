package com.uniabsence.backend.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

final class DatabaseSchema {

	private static final List<String> CREATE_TABLE_STATEMENTS = List.of(
		"""
		CREATE TABLE IF NOT EXISTS users (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			email TEXT NOT NULL COLLATE NOCASE UNIQUE,
			password_hash TEXT NOT NULL,
			role TEXT NOT NULL CHECK (role IN ('student', 'teacher', 'administrator')),
			created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
		)
		""",
		"""
		CREATE TABLE IF NOT EXISTS groups (
			id TEXT PRIMARY KEY,
			name TEXT NOT NULL,
			specialty TEXT,
			main_subject TEXT,
			academic_year TEXT,
			created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
		)
		""",
		"""
		CREATE TABLE IF NOT EXISTS students (
			id INTEGER PRIMARY KEY,
			user_id INTEGER UNIQUE,
			student_number TEXT NOT NULL UNIQUE,
			first_name TEXT NOT NULL,
			last_name TEXT NOT NULL,
			email TEXT NOT NULL COLLATE NOCASE UNIQUE,
			group_id TEXT,
			program TEXT,
			avatar TEXT,
			academic_year TEXT,
			created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
			FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
			FOREIGN KEY (group_id) REFERENCES groups(id) ON UPDATE CASCADE ON DELETE SET NULL
		)
		""",
		"""
		CREATE TABLE IF NOT EXISTS teachers (
			id INTEGER PRIMARY KEY,
			user_id INTEGER UNIQUE,
			teacher_identifier TEXT NOT NULL UNIQUE,
			first_name TEXT NOT NULL,
			last_name TEXT NOT NULL,
			email TEXT NOT NULL COLLATE NOCASE UNIQUE,
			subject TEXT,
			grade TEXT,
			department TEXT,
			avatar TEXT,
			academic_year TEXT,
			created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
			FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
		)
		""",
		"""
		CREATE TABLE IF NOT EXISTS teacher_groups (
			teacher_id INTEGER NOT NULL,
			group_id TEXT NOT NULL,
			PRIMARY KEY (teacher_id, group_id),
			FOREIGN KEY (teacher_id) REFERENCES teachers(id) ON DELETE CASCADE,
			FOREIGN KEY (group_id) REFERENCES groups(id) ON UPDATE CASCADE ON DELETE CASCADE
		)
		""",
		"""
		CREATE TABLE IF NOT EXISTS absences (
			id INTEGER PRIMARY KEY,
			student_id INTEGER NOT NULL,
			teacher_id INTEGER,
			absence_date TEXT NOT NULL,
			subject TEXT NOT NULL,
			teacher_name TEXT,
			session_label TEXT,
			session_type TEXT,
			start_time TEXT,
			end_time TEXT,
			status TEXT NOT NULL DEFAULT 'unjustified'
				CHECK (status IN ('justified', 'unjustified')),
			created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
			FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
			FOREIGN KEY (teacher_id) REFERENCES teachers(id) ON DELETE SET NULL
		)
		""",
		"""
		CREATE TABLE IF NOT EXISTS justifications (
			id INTEGER PRIMARY KEY,
			absence_id INTEGER NOT NULL,
			file_name TEXT NOT NULL,
			file_path TEXT,
			status TEXT NOT NULL DEFAULT 'pending'
				CHECK (status IN ('pending', 'accepted', 'rejected')),
			submitted_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
			reviewed_at TEXT,
			reviewed_by_user_id INTEGER,
			FOREIGN KEY (absence_id) REFERENCES absences(id) ON DELETE CASCADE,
			FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id) ON DELETE SET NULL
		)
		""",
		"""
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
		"""
	);

	private DatabaseSchema() {
	}

	static void createTables(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			for (String sql : CREATE_TABLE_STATEMENTS) {
				statement.executeUpdate(sql);
			}
		}
	}
}
