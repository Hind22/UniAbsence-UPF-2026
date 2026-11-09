package com.uniabsence.backend.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

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
@Order(3)
public class DevelopmentDemoStudents implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(DevelopmentDemoStudents.class);
	private static final String DEMO_TEACHER_EMAIL = "m.kaddour@univ-alger.dz";
	private static final String ACADEMIC_YEAR = "2026-2027";
	private static final List<DemoGroup> GROUPS = List.of(
		new DemoGroup("DEMO-L2-RESEAUX", "Groupe démo L2 Réseaux", "Informatique", "Réseaux informatiques"),
		new DemoGroup(
			"DEMO-L3-GENIE-LOGICIEL",
			"Groupe démo L3 Génie logiciel",
			"Informatique",
			"Génie logiciel"
		)
	);
	private static final List<DemoStudent> STUDENTS = List.of(
		new DemoStudent("DEMO-STUDENT-002", "sara.amrani@demo.uniabsence.dz", "Sara", "Amrani",
			"DEMO-L3-INFO", "Informatique – L3"),
		new DemoStudent("DEMO-STUDENT-003", "yacine.boudiaf@demo.uniabsence.dz", "Yacine", "Boudiaf",
			"DEMO-L3-INFO", "Informatique – L3"),
		new DemoStudent("DEMO-STUDENT-004", "lina.cherif@demo.uniabsence.dz", "Lina", "Cherif",
			"DEMO-L3-INFO", "Informatique – L3"),
		new DemoStudent("DEMO-STUDENT-005", "ilyes.mansouri@demo.uniabsence.dz", "Ilyes", "Mansouri",
			"DEMO-L2-RESEAUX", "Informatique – L2"),
		new DemoStudent("DEMO-STUDENT-006", "nour.haddad@demo.uniabsence.dz", "Nour", "Haddad",
			"DEMO-L2-RESEAUX", "Informatique – L2"),
		new DemoStudent("DEMO-STUDENT-007", "samir.ouali@demo.uniabsence.dz", "Samir", "Ouali",
			"DEMO-L2-RESEAUX", "Informatique – L2"),
		new DemoStudent("DEMO-STUDENT-008", "ines.saadi@demo.uniabsence.dz", "Ines", "Saadi",
			"DEMO-L2-RESEAUX", "Informatique – L2"),
		new DemoStudent("DEMO-STUDENT-009", "rania.merabet@demo.uniabsence.dz", "Rania", "Merabet",
			"DEMO-L3-GENIE-LOGICIEL", "Informatique – L3"),
		new DemoStudent("DEMO-STUDENT-010", "karim.belkacem@demo.uniabsence.dz", "Karim", "Belkacem",
			"DEMO-L3-GENIE-LOGICIEL", "Informatique – L3"),
		new DemoStudent("DEMO-STUDENT-011", "maya.bouzid@demo.uniabsence.dz", "Maya", "Bouzid",
			"DEMO-L3-GENIE-LOGICIEL", "Informatique – L3")
	);

	private final DatabaseConfig databaseConfig;
	private final PasswordEncoder passwordEncoder;

	public DevelopmentDemoStudents(DatabaseConfig databaseConfig, PasswordEncoder passwordEncoder) {
		this.databaseConfig = databaseConfig;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(ApplicationArguments args) throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			connection.setAutoCommit(false);
			try {
				for (DemoGroup group : GROUPS) {
					ensureGroup(connection, group);
				}

				long teacherId = findDemoTeacherId(connection);
				ensureTeacherGroup(connection, teacherId, "DEMO-L3-INFO");
				for (DemoGroup group : GROUPS) {
					ensureTeacherGroup(connection, teacherId, group.id());
				}

				int insertedCount = 0;
				for (DemoStudent student : STUDENTS) {
					if (ensureStudent(connection, student)) {
						insertedCount++;
					}
				}

				connection.commit();
				logger.info(
					"Les étudiants de démonstration sont disponibles (profil dev uniquement, {} nouvel(s) étudiant(s)).",
					insertedCount
				);
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

	private void ensureGroup(Connection connection, DemoGroup group) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO groups (id, name, specialty, main_subject, academic_year)
				VALUES (?, ?, ?, ?, ?)
				ON CONFLICT(id) DO NOTHING
				""")) {
			statement.setString(1, group.id());
			statement.setString(2, group.name());
			statement.setString(3, group.specialty());
			statement.setString(4, group.mainSubject());
			statement.setString(5, ACADEMIC_YEAR);
			statement.executeUpdate();
		}
	}

	private long findDemoTeacherId(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT t.id
				FROM teachers t
				JOIN users u ON u.id = t.user_id
				WHERE u.email = ? AND u.role = 'teacher'
				""")) {
			statement.setString(1, DEMO_TEACHER_EMAIL);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new SQLException("Le profil enseignant de démonstration est introuvable.");
				}
				return resultSet.getLong("id");
			}
		}
	}

	private void ensureTeacherGroup(Connection connection, long teacherId, String groupId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO teacher_groups (teacher_id, group_id)
				VALUES (?, ?)
				ON CONFLICT(teacher_id, group_id) DO NOTHING
				""")) {
			statement.setLong(1, teacherId);
			statement.setString(2, groupId);
			statement.executeUpdate();
		}
	}

	private boolean ensureStudent(Connection connection, DemoStudent student) throws SQLException {
		if (studentExists(connection, student)) {
			return false;
		}

		long userId = ensureStudentUser(connection, student.email());
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO students (
					user_id, student_number, first_name, last_name, email,
					group_id, program, academic_year
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""")) {
			statement.setLong(1, userId);
			statement.setString(2, student.studentNumber());
			statement.setString(3, student.firstName());
			statement.setString(4, student.lastName());
			statement.setString(5, student.email());
			statement.setString(6, student.groupId());
			statement.setString(7, student.program());
			statement.setString(8, ACADEMIC_YEAR);
			statement.executeUpdate();
		}
		return true;
	}

	private boolean studentExists(Connection connection, DemoStudent student) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT 1 FROM students
				WHERE student_number = ? OR email = ?
				LIMIT 1
				""")) {
			statement.setString(1, student.studentNumber());
			statement.setString(2, student.email());
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private long ensureStudentUser(Connection connection, String email) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO users (email, password_hash, role)
				VALUES (?, ?, 'student')
				ON CONFLICT(email) DO NOTHING
				""")) {
			statement.setString(1, email);
			statement.setString(2, passwordEncoder.encode(UUID.randomUUID().toString()));
			statement.executeUpdate();
		}

		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT id, role FROM users WHERE email = ?")) {
			statement.setString(1, email);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next() || !"student".equals(resultSet.getString("role"))) {
					throw new SQLException("L'adresse de démonstration est déjà utilisée par un rôle non étudiant: "
						+ email);
				}
				return resultSet.getLong("id");
			}
		}
	}

	private record DemoGroup(String id, String name, String specialty, String mainSubject) {
	}

	private record DemoStudent(
		String studentNumber,
		String email,
		String firstName,
		String lastName,
		String groupId,
		String program
	) {
	}
}
