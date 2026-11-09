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
@Order(6)
public class DevelopmentDemoStudentData implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(DevelopmentDemoStudentData.class);
	private static final String STUDENT_EMAIL = "demo-student@uniabsence.dz";
	private static final String TEACHER_EMAIL = "m.kaddour@univ-alger.dz";
	private static final List<DemoAbsence> ABSENCES = List.of(
		new DemoAbsence("2026-10-28", "Algorithmique avancée", "Dr. Kaddour",
			"CM 08h00–10h00", "CM", "08:00", "10:00", "unjustified", null),
		new DemoAbsence("2026-10-22", "Réseaux informatiques", "Mme. Haddad",
			"TD 10h00–12h00", "TD", "10:00", "12:00", "justified", "certificat_medical.pdf"),
		new DemoAbsence("2026-10-20", "Bases de données", "M. Rahmani",
			"TP 14h00–16h00", "TP", "14:00", "16:00", "unjustified", null),
		new DemoAbsence("2026-10-15", "Génie logiciel", "Dr. Meziane",
			"CM 08h00–10h00", "CM", "08:00", "10:00", "justified", "ordonnance.pdf"),
		new DemoAbsence("2026-10-10", "Algorithmique avancée", "Dr. Kaddour",
			"TD 13h00–15h00", "TD", "13:00", "15:00", "unjustified", null),
		new DemoAbsence("2026-10-07", "Systèmes d'exploitation", "M. Bouzidi",
			"TP 16h00–18h00", "TP", "16:00", "18:00", "justified", "justificatif_transport.pdf"),
		new DemoAbsence("2026-10-03", "Bases de données", "M. Rahmani",
			"CM 10h00–12h00", "CM", "10:00", "12:00", "unjustified", null),
		new DemoAbsence("2026-09-29", "Réseaux informatiques", "Mme. Haddad",
			"CM 08h00–10h00", "CM", "08:00", "10:00", "unjustified", null)
	);

	private final DatabaseConfig databaseConfig;

	public DevelopmentDemoStudentData(DatabaseConfig databaseConfig) {
		this.databaseConfig = databaseConfig;
	}

	@Override
	public void run(ApplicationArguments args) throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			connection.setAutoCommit(false);
			try {
				StudentProfile student = findDemoStudent(connection);
				Long teacherId = findTeacherId(connection);
				for (DemoAbsence absence : ABSENCES) {
					if (findExistingAbsenceId(connection, student.id(), absence) == null) {
						long absenceId = insertAbsence(connection, student.id(), teacherId, absence);
						if (absence.justificationFileName() != null) {
							insertJustification(connection, absenceId, absence.justificationFileName());
						}
						insertPlannedSession(connection, student, teacherId, absence);
					}
				}
				connection.commit();
				logger.info("Les données d'absence de démonstration sont disponibles (profil dev uniquement).");
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

	private StudentProfile findDemoStudent(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT s.id, s.group_id, s.academic_year
				FROM students s
				JOIN users u ON u.id = s.user_id
				WHERE u.email = ? AND u.role = 'student'
				""")) {
			statement.setString(1, STUDENT_EMAIL);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new SQLException("Le profil étudiant de démonstration est introuvable.");
				}
				String academicYear = resultSet.getString("academic_year");
				return new StudentProfile(
					resultSet.getLong("id"),
					resultSet.getString("group_id"),
					academicYear == null || academicYear.isBlank() ? "2026-2027" : academicYear
				);
			}
		}
	}

	private Long findTeacherId(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT t.id
				FROM teachers t
				JOIN users u ON u.id = t.user_id
				WHERE u.email = ? AND u.role = 'teacher'
				""")) {
			statement.setString(1, TEACHER_EMAIL);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() ? resultSet.getLong("id") : null;
			}
		}
	}

	private Long findExistingAbsenceId(
			Connection connection,
			long studentId,
			DemoAbsence absence) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT id
				FROM absences
				WHERE student_id = ?
				  AND absence_date = ?
				  AND subject = ?
				  AND session_type = ?
				  AND start_time = ?
				  AND end_time = ?
				LIMIT 1
				""")) {
			statement.setLong(1, studentId);
			statement.setString(2, absence.date());
			statement.setString(3, absence.subject());
			statement.setString(4, absence.sessionType());
			statement.setString(5, absence.startTime());
			statement.setString(6, absence.endTime());
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() ? resultSet.getLong("id") : null;
			}
		}
	}

	private long insertAbsence(
			Connection connection,
			long studentId,
			Long teacherId,
			DemoAbsence absence) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO absences (
					student_id, teacher_id, absence_date, subject, teacher_name,
					session_label, session_type, start_time, end_time, status
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")) {
			statement.setLong(1, studentId);
			if (teacherId == null || !"Dr. Kaddour".equals(absence.teacherName())) {
				statement.setNull(2, java.sql.Types.INTEGER);
			} else {
				statement.setLong(2, teacherId);
			}
			statement.setString(3, absence.date());
			statement.setString(4, absence.subject());
			statement.setString(5, absence.teacherName());
			statement.setString(6, absence.sessionLabel());
			statement.setString(7, absence.sessionType());
			statement.setString(8, absence.startTime());
			statement.setString(9, absence.endTime());
			statement.setString(10, absence.status());
			statement.executeUpdate();
		}

		try (Statement statement = connection.createStatement();
			 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
			if (!resultSet.next()) {
				throw new SQLException("SQLite n'a pas retourné l'identifiant de l'absence de démonstration.");
			}
			return resultSet.getLong(1);
		}
	}

	private void insertJustification(Connection connection, long absenceId, String fileName) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO justifications (absence_id, file_name, status)
				VALUES (?, ?, 'accepted')
				""")) {
			statement.setLong(1, absenceId);
			statement.setString(2, fileName);
			statement.executeUpdate();
		}
	}

	private void insertPlannedSession(
			Connection connection,
			StudentProfile student,
			Long teacherId,
			DemoAbsence absence) throws SQLException {
		if (student.groupId() == null || student.groupId().isBlank()) {
			return;
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO planned_sessions (
					group_id, academic_year, session_date, start_time, end_time,
					subject, session_type, teacher_id, room, status
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'completed')
				ON CONFLICT(group_id, academic_year, session_date, start_time, session_type) DO NOTHING
				""")) {
			statement.setString(1, student.groupId());
			statement.setString(2, student.academicYear());
			statement.setString(3, absence.date());
			statement.setString(4, absence.startTime());
			statement.setString(5, absence.endTime());
			statement.setString(6, absence.subject());
			statement.setString(7, absence.sessionType());
			if (teacherId == null || !"Dr. Kaddour".equals(absence.teacherName())) {
				statement.setNull(8, java.sql.Types.INTEGER);
			} else {
				statement.setLong(8, teacherId);
			}
			statement.setString(9, "Salle de démonstration");
			statement.executeUpdate();
		}
	}

	private record StudentProfile(long id, String groupId, String academicYear) {
	}

	private record DemoAbsence(
		String date,
		String subject,
		String teacherName,
		String sessionLabel,
		String sessionType,
		String startTime,
		String endTime,
		String status,
		String justificationFileName
	) {
	}
}
