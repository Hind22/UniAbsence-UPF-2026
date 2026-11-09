package com.uniabsence.backend;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.uniabsence.backend.auth.AuthenticatedUser;
import com.uniabsence.backend.auth.AuthTokenService;
import com.uniabsence.backend.config.DatabaseConfig;
import com.uniabsence.backend.config.DevelopmentDemoStudentData;
import com.uniabsence.backend.config.DevelopmentDemoStudents;
import com.uniabsence.backend.service.JustificationFileStorage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BackendApplicationTests {

	private static final Path TEST_DATABASE_PATH = Path.of(
		System.getProperty("java.io.tmpdir"),
		"uniabsence-api-" + UUID.randomUUID() + ".db"
	);
	private static final Path TEST_JUSTIFICATION_DIRECTORY = Path.of(
		System.getProperty("java.io.tmpdir"),
		"uniabsence-justifications-" + UUID.randomUUID()
	);
	private static final String TEST_DATABASE_URL = "jdbc:sqlite:"
		+ TEST_DATABASE_PATH.toAbsolutePath().toString().replace('\\', '/');
	private static final String TEST_TOKEN_SECRET = "test-only-secret-" + UUID.randomUUID();

	@DynamicPropertySource
	static void configureTestDatabase(DynamicPropertyRegistry registry) {
		registry.add("app.database.url", () -> TEST_DATABASE_URL);
		registry.add("app.auth.token-secret", () -> TEST_TOKEN_SECRET);
		registry.add("app.justifications.storage-dir", () -> TEST_JUSTIFICATION_DIRECTORY.toString());
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DatabaseConfig databaseConfig;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AuthTokenService authTokenService;

	@Autowired
	private DevelopmentDemoStudentData developmentDemoStudentData;

	@Autowired
	private DevelopmentDemoStudents developmentDemoStudents;

	private String testAdminToken;

	@AfterEach
	void removeTeacherUpdateTestFixtures() throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			try (Statement statement = connection.createStatement()) {
				statement.executeUpdate("""
					DELETE FROM students
					WHERE student_number LIKE 'FILE-STU-assigned-%'
					   OR student_number LIKE 'FILE-STU-outside-%'
					""");
				statement.executeUpdate("""
					DELETE FROM teachers
					WHERE teacher_identifier LIKE 'FILE-TEACHER-update-%'
					   OR teacher_identifier LIKE 'FILE-TEACHER-no-groups-%'
					""");
				statement.executeUpdate("""
					DELETE FROM users
					WHERE email LIKE 'update-%@example.test'
					""");
				statement.executeUpdate("DELETE FROM groups WHERE id LIKE 'UPDATE-G%-%'");
			}
		}
	}

	@BeforeEach
	void createTestAdminToken() throws SQLException {
		removeAccountTestFixtures();
		testAdminToken = authTokenService.createToken(
			new AuthenticatedUser(
				findUserId("scolarite@univ-alger.dz"),
				"scolarite@univ-alger.dz",
				"ADMIN"
			)
		);
	}

	private void removeAccountTestFixtures() throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM students WHERE student_number LIKE 'ACC-ETU-%'")) {
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM teachers WHERE teacher_identifier LIKE 'ACC-ENS-%'")) {
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM users WHERE email LIKE 'account-%@example.test' OR email LIKE 'updated-account-%@example.test'")) {
				statement.executeUpdate();
			}
		}
	}

	private ResultActions performAuthenticated(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + testAdminToken));
	}

	private int authenticatedArraySize(String path) throws Exception {
		String response = performAuthenticated(get(path))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return objectMapper.readTree(response).size();
	}

	private ResultActions performAs(
			AbstractMockHttpServletRequestBuilder<?> request,
			long userId,
			String email,
			String role) throws Exception {
		String token = authTokenService.createToken(new AuthenticatedUser(userId, email, role));
		return mockMvc.perform(request.header("Authorization", "Bearer " + token));
	}

	private long insertTestUser(Connection connection, String email, String role) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"INSERT INTO users (email, password_hash, role) VALUES (?, 'test-hash', ?)")) {
			statement.setString(1, email);
			statement.setString(2, role.toLowerCase(java.util.Locale.ROOT));
			statement.executeUpdate();
		}
		return lastInsertedId(connection);
	}

	private long insertTestStudent(
			Connection connection,
			long userId,
			String groupId,
			String suffix,
			String label) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO students (user_id, student_number, first_name, last_name, email, group_id)
				VALUES (?, ?, 'Test', ?, ?, ?)
				""")) {
			statement.setLong(1, userId);
			statement.setString(2, "FILE-STU-" + label + "-" + suffix);
			statement.setString(3, label);
			statement.setString(4, "file-" + label + "-" + suffix + "@example.test");
			statement.setString(5, groupId);
			statement.executeUpdate();
		}
		return lastInsertedId(connection);
	}

	private long insertTestTeacher(
			Connection connection,
			long userId,
			String suffix,
			String label) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO teachers (user_id, teacher_identifier, first_name, last_name, email)
				VALUES (?, ?, 'Test', ?, ?)
				""")) {
			statement.setLong(1, userId);
			statement.setString(2, "FILE-TEACHER-" + label + "-" + suffix);
			statement.setString(3, label);
			statement.setString(4, "file-" + label + "-teacher-" + suffix + "@example.test");
			statement.executeUpdate();
		}
		return lastInsertedId(connection);
	}

	private long lastInsertedId(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement();
			 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
			if (!resultSet.next()) {
				throw new SQLException("SQLite n'a pas retourné l'identifiant inséré.");
			}
			return resultSet.getLong(1);
		}
	}

	@AfterAll
	static void removeTestDatabase() throws IOException {
		Files.deleteIfExists(TEST_DATABASE_PATH);
		Files.deleteIfExists(Path.of(TEST_DATABASE_PATH + "-journal"));
		Files.deleteIfExists(Path.of(TEST_DATABASE_PATH + "-shm"));
		Files.deleteIfExists(Path.of(TEST_DATABASE_PATH + "-wal"));
		if (Files.exists(TEST_JUSTIFICATION_DIRECTORY)) {
			try (var paths = Files.walk(TEST_JUSTIFICATION_DIRECTORY)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
					Files.deleteIfExists(path);
				}
			}
		}
	}

	@Test
	void contextLoads() {
	}

	@Test
	void teacherAttendanceStatsRecalculateAfterAbsenceChanges() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String plannedGroup = "RATE-G1-" + suffix;
		String unplannedGroup = "RATE-G0-" + suffix;
		String teacherEmail = "rate-teacher-" + suffix + "@example.test";
		String plannedStudentEmail = "rate-planned-" + suffix + "@example.test";
		String unplannedStudentEmail = "rate-unplanned-" + suffix + "@example.test";
		long teacherUserId = 0;
		long teacherId = 0;
		long plannedStudentUserId = 0;
		long unplannedStudentUserId = 0;
		long plannedStudentId = 0;
		long unplannedStudentId = 0;

		try {
			try (Connection connection = databaseConfig.openConnection()) {
				try (PreparedStatement statement = connection.prepareStatement(
						"INSERT INTO groups (id, name, academic_year) VALUES (?, ?, '2026-2027'), (?, ?, '2026-2027')")) {
					statement.setString(1, plannedGroup);
					statement.setString(2, "Groupe avec séances");
					statement.setString(3, unplannedGroup);
					statement.setString(4, "Groupe sans séances");
					statement.executeUpdate();
				}
				teacherUserId = insertTestUser(connection, teacherEmail, "teacher");
				plannedStudentUserId = insertTestUser(connection, plannedStudentEmail, "student");
				unplannedStudentUserId = insertTestUser(connection, unplannedStudentEmail, "student");
				teacherId = insertTestTeacher(connection, teacherUserId, suffix, "rate");
				plannedStudentId = insertTestStudent(connection, plannedStudentUserId, plannedGroup, suffix, "rate-planned");
				unplannedStudentId = insertTestStudent(connection, unplannedStudentUserId, unplannedGroup, suffix, "rate-unplanned");

				try (PreparedStatement statement = connection.prepareStatement(
						"INSERT INTO teacher_groups (teacher_id, group_id) VALUES (?, ?), (?, ?)")) {
					statement.setLong(1, teacherId);
					statement.setString(2, plannedGroup);
					statement.setLong(3, teacherId);
					statement.setString(4, unplannedGroup);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO planned_sessions (
							group_id, academic_year, session_date, start_time, end_time,
							subject, session_type, status
						) VALUES (?, '2026-2027', ?, '08:00', '10:00', 'Algorithmique', 'CM', 'scheduled')
						""")) {
					for (int day = 1; day <= 20; day++) {
						statement.setString(1, plannedGroup);
						statement.setString(2, "2026-10-%02d".formatted(day));
						statement.executeUpdate();
					}
				}
			}

			String teacherToken = authTokenService.createToken(new AuthenticatedUser(
				teacherUserId, teacherEmail, "TEACHER"));
			String statsPath = "/api/v1/students/" + plannedStudentId + "/attendance-stats";
			mockMvc.perform(get(statsPath).header("Authorization", "Bearer " + teacherToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plannedSessionsCount").value(20))
				.andExpect(jsonPath("$.distinctMissedSessionsCount").value(0))
				.andExpect(jsonPath("$.absenceRate").value(0.0));

			long[] absenceIds = new long[4];
			for (int day = 1; day <= absenceIds.length; day++) {
				String body = """
					{
					  "studentId": %d,
					  "absenceDate": "2026-10-%02d",
					  "subject": "Algorithmique",
					  "sessionType": "CM",
					  "startTime": "08:00",
					  "endTime": "10:00"
					}
					""".formatted(plannedStudentId, day);
				String response = mockMvc.perform(post("/api/v1/absences")
						.header("Authorization", "Bearer " + teacherToken)
						.contentType("application/json")
						.content(body))
					.andExpect(status().isCreated())
					.andReturn()
					.getResponse()
					.getContentAsString();
				absenceIds[day - 1] = objectMapper.readTree(response).path("id").asLong();
			}

			mockMvc.perform(get(statsPath).header("Authorization", "Bearer " + teacherToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.distinctMissedSessionsCount").value(4))
				.andExpect(jsonPath("$.totalAbsencesCount").value(4))
				.andExpect(jsonPath("$.unjustifiedAbsencesCount").value(4))
				.andExpect(jsonPath("$.absenceRate").value(20.0));

			String duplicateMeeting = """
				{
				  "studentId": %d,
				  "absenceDate": "2026-10-01",
				  "subject": "Matière renommée",
				  "sessionType": "CM",
				  "startTime": "08:00",
				  "endTime": "10:00"
				}
				""".formatted(plannedStudentId);
			mockMvc.perform(post("/api/v1/absences")
					.header("Authorization", "Bearer " + teacherToken)
					.contentType("application/json")
					.content(duplicateMeeting))
				.andExpect(status().isCreated());
			mockMvc.perform(get(statsPath).header("Authorization", "Bearer " + teacherToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.distinctMissedSessionsCount").value(4))
				.andExpect(jsonPath("$.totalAbsencesCount").value(4))
				.andExpect(jsonPath("$.unjustifiedAbsencesCount").value(4))
				.andExpect(jsonPath("$.absenceRate").value(20.0));

			String updatedAbsence = """
				{
				  "studentId": %d,
				  "absenceDate": "2026-12-01",
				  "subject": "Algorithmique",
				  "sessionType": "CM",
				  "startTime": "08:00",
				  "endTime": "10:00"
				}
				""".formatted(plannedStudentId);
			mockMvc.perform(put("/api/v1/absences/{id}", absenceIds[3])
					.header("Authorization", "Bearer " + teacherToken)
					.contentType("application/json")
					.content(updatedAbsence))
				.andExpect(status().isOk());
			mockMvc.perform(get(statsPath).header("Authorization", "Bearer " + teacherToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.distinctMissedSessionsCount").value(3))
				.andExpect(jsonPath("$.totalAbsencesCount").value(3))
				.andExpect(jsonPath("$.absenceRate").value(15.0));

			mockMvc.perform(delete("/api/v1/absences/{id}", absenceIds[1])
					.header("Authorization", "Bearer " + teacherToken))
				.andExpect(status().isNoContent());
			mockMvc.perform(get(statsPath).header("Authorization", "Bearer " + teacherToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.distinctMissedSessionsCount").value(2))
				.andExpect(jsonPath("$.totalAbsencesCount").value(2))
				.andExpect(jsonPath("$.absenceRate").value(10.0));

			String unplannedAbsence = """
				{
				  "studentId": %d,
				  "absenceDate": "2026-10-05",
				  "subject": "Réseaux",
				  "sessionType": "TD",
				  "startTime": "10:00",
				  "endTime": "12:00"
				}
				""".formatted(unplannedStudentId);
			mockMvc.perform(post("/api/v1/absences")
					.header("Authorization", "Bearer " + teacherToken)
					.contentType("application/json")
					.content(unplannedAbsence))
				.andExpect(status().isCreated());

			String unplannedStats = mockMvc.perform(get(
					"/api/v1/students/" + unplannedStudentId + "/attendance-stats")
					.header("Authorization", "Bearer " + teacherToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plannedSessionsCount").value(0))
				.andExpect(jsonPath("$.distinctMissedSessionsCount").value(1))
				.andExpect(jsonPath("$.totalAbsencesCount").value(1))
				.andExpect(jsonPath("$.unjustifiedAbsencesCount").value(1))
				.andReturn()
				.getResponse()
				.getContentAsString();
			JsonNode noPlannedSessions = objectMapper.readTree(unplannedStats);
			assertTrue(noPlannedSessions.path("absenceRate").isNull());
			assertTrue(noPlannedSessions.path("explanation").asText().contains("aucune séance prévue"));
		} finally {
			try (Connection connection = databaseConfig.openConnection()) {
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM students WHERE user_id IN (?, ?)")) {
					statement.setLong(1, plannedStudentUserId);
					statement.setLong(2, unplannedStudentUserId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM teachers WHERE user_id = ?")) {
					statement.setLong(1, teacherUserId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM users WHERE id IN (?, ?, ?)")) {
					statement.setLong(1, teacherUserId);
					statement.setLong(2, plannedStudentUserId);
					statement.setLong(3, unplannedStudentUserId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM groups WHERE id IN (?, ?)")) {
					statement.setString(1, plannedGroup);
					statement.setString(2, unplannedGroup);
					statement.executeUpdate();
				}
			}
		}
	}

	@Test
	void justificationFilesAreValidatedStoredAndScopedToStudentAndTeacherGroups() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String ownedGroup = "FILE-G1-" + suffix;
		String otherGroup = "FILE-G2-" + suffix;
		String ownerEmail = "file-owner-" + suffix + "@example.test";
		String otherStudentEmail = "file-other-" + suffix + "@example.test";
		String assignedTeacherEmail = "file-assigned-" + suffix + "@example.test";
		String otherTeacherEmail = "file-other-teacher-" + suffix + "@example.test";
		long ownerUserId;
		long otherStudentUserId;
		long assignedTeacherUserId;
		long otherTeacherUserId;
		long ownerStudentId;
		long absenceId;

		try (Connection connection = databaseConfig.openConnection()) {
			try (PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO groups (id, name) VALUES (?, ?), (?, ?)")) {
				statement.setString(1, ownedGroup);
				statement.setString(2, "Groupe fichier attribué");
				statement.setString(3, otherGroup);
				statement.setString(4, "Groupe fichier non attribué");
				statement.executeUpdate();
			}
			ownerUserId = insertTestUser(connection, ownerEmail, "student");
			otherStudentUserId = insertTestUser(connection, otherStudentEmail, "student");
			assignedTeacherUserId = insertTestUser(connection, assignedTeacherEmail, "teacher");
			otherTeacherUserId = insertTestUser(connection, otherTeacherEmail, "teacher");
			ownerStudentId = insertTestStudent(connection, ownerUserId, ownedGroup, suffix, "owner");
			long otherStudentId = insertTestStudent(connection, otherStudentUserId, otherGroup, suffix, "other");
			long assignedTeacherId = insertTestTeacher(connection, assignedTeacherUserId, suffix, "assigned");
			long otherTeacherId = insertTestTeacher(connection, otherTeacherUserId, suffix, "other");
			try (PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO teacher_groups (teacher_id, group_id) VALUES (?, ?), (?, ?)")) {
				statement.setLong(1, assignedTeacherId);
				statement.setString(2, ownedGroup);
				statement.setLong(3, otherTeacherId);
				statement.setString(4, otherGroup);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO absences (student_id, absence_date, subject)
					VALUES (?, '2026-10-30', 'Droit des fichiers')
					""")) {
				statement.setLong(1, ownerStudentId);
				statement.executeUpdate();
			}
			try (Statement statement = connection.createStatement();
				 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
				assertTrue(resultSet.next());
				absenceId = resultSet.getLong(1);
			}
			assertTrue(otherStudentId > 0);
		}

		byte[] validPdf = "%PDF-1.7\nUniAbsence temporary test file\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		MockMultipartFile pdf = new MockMultipartFile(
			"file",
			"certificat médical.pdf",
			"application/pdf",
			validPdf
		);
		String uploadedJson = performAs(
				multipart("/api/v1/justifications/upload").file(pdf)
					.param("absenceId", String.valueOf(absenceId)),
				ownerUserId,
				ownerEmail,
				"STUDENT")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.absenceId").value(absenceId))
			.andExpect(jsonPath("$.studentId").value(ownerStudentId))
			.andExpect(jsonPath("$.fileName").value("certificat médical.pdf"))
			.andExpect(jsonPath("$.status").value("pending"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long justificationId = objectMapper.readTree(uploadedJson).get("id").asLong();
		String storageKey = objectMapper.readTree(uploadedJson).get("filePath").asText();
		assertTrue(storageKey.matches("^[0-9a-f-]{36}\\.pdf$"));
		assertTrue(Files.isRegularFile(TEST_JUSTIFICATION_DIRECTORY.resolve(storageKey)));
		for (String imageType : List.of("png", "jpeg")) {
			ByteArrayOutputStream imageContents = new ByteArrayOutputStream();
			assertTrue(ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), imageType, imageContents));
			String extension = "jpeg".equals(imageType) ? "jpg" : imageType;
			performAs(multipart("/api/v1/justifications/upload")
					.file(new MockMultipartFile(
						"file",
						"medical-image." + extension,
						"image/" + imageType,
						imageContents.toByteArray()
					))
					.param("absenceId", String.valueOf(absenceId)),
					ownerUserId, ownerEmail, "STUDENT")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fileName").value("medical-image." + extension));
		}

		performAs(get("/api/v1/justifications/{id}/file", justificationId),
				ownerUserId, ownerEmail, "STUDENT")
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/pdf"))
			.andExpect(header().string("Content-Disposition", containsString("attachment")))
			.andExpect(content().bytes(validPdf));
		performAs(get("/api/v1/justifications/{id}/file", justificationId),
				assignedTeacherUserId, assignedTeacherEmail, "TEACHER")
			.andExpect(status().isOk())
			.andExpect(content().bytes(validPdf));
		performAs(get("/api/v1/justifications/{id}/file", justificationId),
				otherStudentUserId, otherStudentEmail, "STUDENT")
			.andExpect(status().isForbidden());
		performAs(get("/api/v1/justifications/{id}/file", justificationId),
				otherTeacherUserId, otherTeacherEmail, "TEACHER")
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/justifications/{id}/file", justificationId))
			.andExpect(status().isUnauthorized());
		performAs(multipart("/api/v1/justifications/upload")
				.file(new MockMultipartFile("file", "forbidden.pdf", "application/pdf", validPdf))
				.param("absenceId", String.valueOf(absenceId)),
				otherStudentUserId, otherStudentEmail, "STUDENT")
			.andExpect(status().isForbidden());

		performAs(multipart("/api/v1/justifications/upload")
				.file(new MockMultipartFile("file", "spoofed.pdf", "application/pdf", "not a pdf".getBytes()))
				.param("absenceId", String.valueOf(absenceId)),
				ownerUserId, ownerEmail, "STUDENT")
			.andExpect(status().isBadRequest());
		performAs(multipart("/api/v1/justifications/upload")
				.file(new MockMultipartFile("file", "oversized.pdf", "application/pdf",
					new byte[(int) JustificationFileStorage.MAX_FILE_SIZE_BYTES + 1]))
				.param("absenceId", String.valueOf(absenceId)),
				ownerUserId, ownerEmail, "STUDENT")
			.andExpect(status().isPayloadTooLarge());
		performAs(multipart("/api/v1/justifications/upload")
				.file(new MockMultipartFile("file", "valid.pdf", "application/pdf", validPdf))
				.param("absenceId", "0"),
				ownerUserId, ownerEmail, "STUDENT")
			.andExpect(status().isBadRequest());

		performAs(patch("/api/v1/justifications/{id}/decision", justificationId)
				.contentType("application/json")
				.content("""
					{"status":"rejected"}
					"""),
				assignedTeacherUserId, assignedTeacherEmail, "TEACHER")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("rejected"));
		performAs(get("/api/v1/justifications/{id}/file", justificationId),
				assignedTeacherUserId, assignedTeacherEmail, "TEACHER")
			.andExpect(status().isOk())
			.andExpect(content().bytes(validPdf));

		try (Connection connection = databaseConfig.openConnection()) {
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO justifications (absence_id, file_name, file_path)
					VALUES (?, 'traversal.pdf', '../../application.properties')
					""")) {
				statement.setLong(1, absenceId);
				statement.executeUpdate();
			}
			try (Statement statement = connection.createStatement();
				 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
				assertTrue(resultSet.next());
				long unsafePathJustificationId = resultSet.getLong(1);
				performAs(get("/api/v1/justifications/{id}/file", unsafePathJustificationId),
						ownerUserId, ownerEmail, "STUDENT")
					.andExpect(status().isNotFound());
			}
		}
		try (Connection connection = databaseConfig.openConnection()) {
			try (PreparedStatement statement = connection.prepareStatement("""
					DELETE FROM justifications
					WHERE absence_id IN (
						SELECT a.id
						FROM absences a
						JOIN students s ON s.id = a.student_id
						WHERE s.student_number LIKE ?
					)
					""")) {
				statement.setString(1, "FILE-STU-%-" + suffix);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					DELETE FROM absences
					WHERE student_id IN (SELECT id FROM students WHERE student_number LIKE ?)
					""")) {
				statement.setString(1, "FILE-STU-%-" + suffix);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					DELETE FROM teacher_groups
					WHERE teacher_id IN (SELECT id FROM teachers WHERE teacher_identifier LIKE ?)
					""")) {
				statement.setString(1, "FILE-TEACHER-%-" + suffix);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM students WHERE student_number LIKE ?")) {
				statement.setString(1, "FILE-STU-%-" + suffix);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM teachers WHERE teacher_identifier LIKE ?")) {
				statement.setString(1, "FILE-TEACHER-%-" + suffix);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM groups WHERE id IN (?, ?)")) {
				statement.setString(1, ownedGroup);
				statement.setString(2, otherGroup);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM users WHERE email IN (?, ?, ?, ?)")) {
				statement.setString(1, ownerEmail);
				statement.setString(2, otherStudentEmail);
				statement.setString(3, assignedTeacherEmail);
				statement.setString(4, otherTeacherEmail);
				statement.executeUpdate();
			}
		}
	}

	@Test
	void adminCanCreateLinkedStudentAndTeacherAccountsWithoutExposingPasswords() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String studentEmail = "account-student-" + suffix + "@example.test";
		String teacherEmail = "account-teacher-" + suffix + "@example.test";
		String studentPassword = "InitialStudent2026!";
		String teacherPassword = "InitialTeacher2026!";

		String studentJson = performAuthenticated(post("/api/v1/students")
				.contentType("application/json")
				.content("""
					{"studentNumber":"ACC-ETU-%s","firstName":"Nadia","lastName":"Test","email":"%s"}
					""".formatted(suffix, studentEmail)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long studentId = objectMapper.readTree(studentJson).get("id").asLong();

		String studentAccountJson = performAuthenticated(post("/api/v1/users")
				.contentType("application/json")
				.content("""
					{"profileType":"STUDENT","profileId":%d,"initialPassword":"%s"}
					""".formatted(studentId, studentPassword)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value(studentEmail))
			.andExpect(jsonPath("$.role").value("STUDENT"))
			.andExpect(jsonPath("$.profileType").value("STUDENT"))
			.andExpect(jsonPath("$.profileId").value(studentId))
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andExpect(jsonPath("$.initialPassword").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long studentUserId = objectMapper.readTree(studentAccountJson).get("id").asLong();

		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(
					"SELECT password_hash, role FROM users WHERE id = ?")) {
			statement.setLong(1, studentUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				assertTrue(resultSet.next());
				assertNotEquals(studentPassword, resultSet.getString("password_hash"));
				assertTrue(resultSet.getString("password_hash").startsWith("$2a$"));
				assertEquals("student", resultSet.getString("role"));
			}
		}
		performAuthenticated(get("/api/v1/students/{id}", studentId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").value(studentUserId));
		String studentLogin = mockMvc.perform(post("/api/v1/auth/login")
				.contentType("application/json")
				.content("""
					{"email":"%s","password":"%s"}
					""".formatted(studentEmail, studentPassword)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("STUDENT"))
			.andExpect(jsonPath("$.user.studentId").value(studentId))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String studentToken = objectMapper.readTree(studentLogin).get("token").asText();
		mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isForbidden());

		performAuthenticated(post("/api/v1/users")
				.contentType("application/json")
				.content("""
					{"profileType":"STUDENT","profileId":%d,"initialPassword":"%s"}
					""".formatted(studentId, studentPassword)))
			.andExpect(status().isConflict());
		performAuthenticated(post("/api/v1/users")
				.contentType("application/json")
				.content("""
					{"profileType":"ADMIN","profileId":%d,"initialPassword":"%s"}
					""".formatted(studentId, studentPassword)))
			.andExpect(status().isBadRequest());
		performAuthenticated(post("/api/v1/users")
				.contentType("application/json")
				.content("""
					{"profileType":"STUDENT","profileId":999999999,"initialPassword":"ValidPassword2026!"}
					"""))
			.andExpect(status().isNotFound());
		performAuthenticated(post("/api/v1/users")
				.contentType("application/json")
				.content("""
					{"profileType":"STUDENT","profileId":%d,"initialPassword":"short"}
					""".formatted(studentId)))
			.andExpect(status().isBadRequest());

		String teacherJson = performAuthenticated(post("/api/v1/teachers")
				.contentType("application/json")
				.content("""
					{"teacherIdentifier":"ACC-ENS-%s","firstName":"Karim","lastName":"Test","email":"%s","groupIds":[]}
					""".formatted(suffix, teacherEmail)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long teacherId = objectMapper.readTree(teacherJson).get("id").asLong();
		String teacherAccountJson = performAuthenticated(post("/api/v1/users")
				.contentType("application/json")
				.content("""
					{"profileType":"TEACHER","profileId":%d,"initialPassword":"%s"}
					""".formatted(teacherId, teacherPassword)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value("TEACHER"))
			.andExpect(jsonPath("$.profileId").value(teacherId))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long teacherUserId = objectMapper.readTree(teacherAccountJson).get("id").asLong();
		performAuthenticated(put("/api/v1/users/{id}", teacherUserId)
				.contentType("application/json")
				.content("""
					{"email":"%s"}
					""".formatted(studentEmail)))
			.andExpect(status().isConflict());
		performAuthenticated(get("/api/v1/teachers/{id}", teacherId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(teacherEmail));
		String teacherLogin = mockMvc.perform(post("/api/v1/auth/login")
				.contentType("application/json")
				.content("""
					{"email":"%s","password":"%s"}
					""".formatted(teacherEmail, teacherPassword)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("TEACHER"))
			.andExpect(jsonPath("$.user.teacherId").value(teacherId))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String teacherToken = objectMapper.readTree(teacherLogin).get("token").asText();
		mockMvc.perform(post("/api/v1/users")
				.header("Authorization", "Bearer " + teacherToken)
				.contentType("application/json")
				.content("""
					{"profileType":"TEACHER","profileId":%d,"initialPassword":"BlockedPassword2026!"}
					""".formatted(teacherId)))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/users"))
			.andExpect(status().isUnauthorized());
		String accountList = performAuthenticated(get("/api/v1/users"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == %d)].role".formatted(teacherUserId)).value("TEACHER"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertFalse(accountList.contains("password_hash"));
		assertFalse(accountList.contains("passwordHash"));
		assertFalse(accountList.contains("initialPassword"));

		String newEmail = "updated-account-" + suffix + "@example.test";
		String updatedPassword = "UpdatedStudent2026!";
		performAuthenticated(put("/api/v1/users/{id}", studentUserId)
				.contentType("application/json")
				.content("""
					{"email":"%s","newPassword":"%s"}
					""".formatted(newEmail, updatedPassword)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(newEmail))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
		performAuthenticated(get("/api/v1/students/{id}", studentId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(newEmail));
		mockMvc.perform(post("/api/v1/auth/login")
				.contentType("application/json")
				.content("""
					{"email":"%s","password":"%s"}
					""".formatted(newEmail, updatedPassword)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("STUDENT"));
		mockMvc.perform(post("/api/v1/auth/login")
				.contentType("application/json")
				.content("""
					{"email":"%s","password":"%s"}
					""".formatted(studentEmail, studentPassword)))
			.andExpect(status().isUnauthorized());
		performAuthenticated(put("/api/v1/users/{id}", findUserId("scolarite@univ-alger.dz"))
				.contentType("application/json")
				.content("""
					{"newPassword":"AttemptedAdminPassword2026!"}
					"""))
			.andExpect(status().isForbidden());

		try (Connection connection = databaseConfig.openConnection()) {
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM users WHERE id IN (?, ?)")) {
				statement.setLong(1, studentUserId);
				statement.setLong(2, teacherUserId);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM students WHERE id = ?")) {
				statement.setLong(1, studentId);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM teachers WHERE id = ?")) {
				statement.setLong(1, teacherId);
				statement.executeUpdate();
			}
		}
	}

	@Test
	void loginAuthenticatesDemoRolesAndProtectsApiEndpoints() throws Exception {
		long studentUserId = findUserId("demo-student@uniabsence.dz");
		long teacherUserId = findUserId("m.kaddour@univ-alger.dz");
		long studentId = findProfileId("students", studentUserId);
		long teacherId = findProfileId("teachers", teacherUserId);

		String studentLogin = mockMvc.perform(post("/api/v1/auth/login")
				.contentType("application/json")
				.content("""
					{"email":"demo-student@uniabsence.dz","password":"StudentDemo2026!","role":"administrator"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("STUDENT"))
			.andExpect(jsonPath("$.user.role").value("STUDENT"))
			.andExpect(jsonPath("$.user.studentId").value(studentId))
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String studentToken = objectMapper.readTree(studentLogin).get("token").asText();

		mockMvc.perform(get("/api/v1/students/me")
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(studentId))
			.andExpect(jsonPath("$.firstName").value("Ahmed"))
			.andExpect(jsonPath("$.lastName").value("Benali"))
			.andExpect(jsonPath("$.groupId").value("DEMO-L3-INFO"));
		String absenceResponse = mockMvc.perform(get("/api/v1/absences")
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(8))
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode absenceRows = objectMapper.readTree(absenceResponse);
		int justifiedAbsences = 0;
		for (JsonNode absence : absenceRows) {
			if ("justified".equals(absence.path("status").asText())) {
				justifiedAbsences++;
			}
		}
		assertEquals(3, justifiedAbsences);

		String justificationResponse = mockMvc.perform(get("/api/v1/justifications")
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode justificationRows = objectMapper.readTree(justificationResponse);
		for (JsonNode justification : justificationRows) {
			assertEquals("accepted", justification.path("status").asText());
		}
		mockMvc.perform(get("/api/v1/students/me/attendance-stats")
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.studentId").value(studentId))
			.andExpect(jsonPath("$.plannedSessionsCount").value(22))
			.andExpect(jsonPath("$.distinctMissedSessionsCount").value(7))
			.andExpect(jsonPath("$.absenceRate").value(31.8));
		mockMvc.perform(get("/api/v1/students/me/planned-sessions")
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(22));
		mockMvc.perform(get("/api/v1/planned-sessions")
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(22));

		developmentDemoStudentData.run(new DefaultApplicationArguments(new String[0]));
		mockMvc.perform(get("/api/v1/absences")
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(8));
		mockMvc.perform(get("/api/v1/justifications")
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3));

		String teacherLogin = mockMvc.perform(post("/api/v1/auth/login")
				.contentType("application/json")
				.content("""
					{"email":"m.kaddour@univ-alger.dz","password":"TeacherDemo2026!","role":"student"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("TEACHER"))
			.andExpect(jsonPath("$.user.teacherId").value(teacherId))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String teacherToken = objectMapper.readTree(teacherLogin).get("token").asText();
		mockMvc.perform(get("/api/v1/teachers/me")
				.header("Authorization", "Bearer " + teacherToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(teacherId));

		String adminLogin = mockMvc.perform(post("/api/v1/auth/login")
				.contentType("application/json")
				.content("""
					{"email":"scolarite@univ-alger.dz","password":"AdminDemo2026!"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andExpect(jsonPath("$.user.role").value("ADMIN"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String adminToken = objectMapper.readTree(adminLogin).get("token").asText();

		mockMvc.perform(post("/api/v1/auth/login")
				.contentType("application/json")
				.content("""
					{"email":"demo-student@uniabsence.dz","password":"wrong-password"}
					"""))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/auth/login")
				.contentType("application/json")
				.content("""
					{"email":"unknown@uniabsence.dz","password":"StudentDemo2026!"}
					"""))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/groups"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/groups")
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/groups")
				.header("Authorization", "Bearer " + teacherToken))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/groups")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk());

		try (Connection connection = databaseConfig.openConnection()) {
			try (PreparedStatement statement = connection.prepareStatement(
					"SELECT password_hash FROM users WHERE email = ?")) {
				for (String email : List.of(
						"demo-student@uniabsence.dz",
						"m.kaddour@univ-alger.dz",
						"scolarite@univ-alger.dz")) {
					statement.setString(1, email);
					try (ResultSet resultSet = statement.executeQuery()) {
						assertTrue(resultSet.next());
						assertTrue(resultSet.getString("password_hash").startsWith("$2"));
					}
				}
			}
		}
	}

	@Test
	void developmentDemoStudentsAreRelatedIdempotentAndHaveNoAbsences() throws Exception {
		long ahmedId = findProfileId("students", findUserId("demo-student@uniabsence.dz"));
		int ahmedAbsencesBefore = countRows(
			"SELECT COUNT(*) FROM absences WHERE student_id = ?",
			ahmedId
		);
		int ahmedJustificationsBefore = countRows("""
			SELECT COUNT(*) FROM justifications j
			JOIN absences a ON a.id = j.absence_id
			WHERE a.student_id = ?
			""", ahmedId);

		developmentDemoStudents.run(new DefaultApplicationArguments(new String[0]));
		developmentDemoStudents.run(new DefaultApplicationArguments(new String[0]));

		try (Connection connection = databaseConfig.openConnection()) {
			try (PreparedStatement statement = connection.prepareStatement("""
					SELECT COUNT(*)
					FROM students s
					JOIN users u ON u.id = s.user_id AND u.role = 'student'
					WHERE s.student_number BETWEEN 'DEMO-STUDENT-002' AND 'DEMO-STUDENT-011'
					""");
				 ResultSet resultSet = statement.executeQuery()) {
				assertTrue(resultSet.next());
				assertEquals(10, resultSet.getInt(1));
			}

			try (PreparedStatement statement = connection.prepareStatement("""
					SELECT g.id, COUNT(s.id) AS student_count
					FROM groups g
					LEFT JOIN students s ON s.group_id = g.id
					WHERE g.id IN ('DEMO-L3-INFO', 'DEMO-L2-RESEAUX', 'DEMO-L3-GENIE-LOGICIEL')
					GROUP BY g.id
					""");
				 ResultSet resultSet = statement.executeQuery()) {
				int groupCount = 0;
				while (resultSet.next()) {
					groupCount++;
					switch (resultSet.getString("id")) {
						case "DEMO-L3-INFO" -> assertEquals(4, resultSet.getInt("student_count"));
						case "DEMO-L2-RESEAUX" -> assertEquals(4, resultSet.getInt("student_count"));
						case "DEMO-L3-GENIE-LOGICIEL" -> assertEquals(3, resultSet.getInt("student_count"));
						default -> throw new AssertionError("Unexpected demo group.");
					}
				}
				assertEquals(3, groupCount);
			}

			try (PreparedStatement statement = connection.prepareStatement("""
					SELECT COUNT(*)
					FROM absences a
					JOIN students s ON s.id = a.student_id
					WHERE s.student_number BETWEEN 'DEMO-STUDENT-002' AND 'DEMO-STUDENT-011'
					""");
				 ResultSet resultSet = statement.executeQuery()) {
				assertTrue(resultSet.next());
				assertEquals(0, resultSet.getInt(1));
			}
		}

		assertEquals(ahmedAbsencesBefore, countRows(
			"SELECT COUNT(*) FROM absences WHERE student_id = ?",
			ahmedId
		));
		assertEquals(ahmedJustificationsBefore, countRows("""
			SELECT COUNT(*) FROM justifications j
			JOIN absences a ON a.id = j.absence_id
			WHERE a.student_id = ?
			""", ahmedId));

		long teacherUserId = findUserId("m.kaddour@univ-alger.dz");
		performAs(get("/api/v1/teachers/me/groups"), teacherUserId, "m.kaddour@univ-alger.dz", "TEACHER")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3));
		performAs(get("/api/v1/students"), teacherUserId, "m.kaddour@univ-alger.dz", "TEACHER")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(11));
	}

	private int countRows(String sql, long value) throws SQLException {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, value);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new SQLException("La requête de comptage n'a retourné aucun résultat.");
				}
				return resultSet.getInt(1);
			}
		}
	}

	private long findUserId(String email) throws SQLException {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement("SELECT id FROM users WHERE email = ?")) {
			statement.setString(1, email);
			try (ResultSet resultSet = statement.executeQuery()) {
				assertTrue(resultSet.next());
				return resultSet.getLong("id");
			}
		}
	}

	private long findProfileId(String table, long userId) throws SQLException {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(
					"SELECT id FROM " + table + " WHERE user_id = ?")) {
			statement.setLong(1, userId);
			try (ResultSet resultSet = statement.executeQuery()) {
				assertTrue(resultSet.next());
				return resultSet.getLong("id");
			}
		}
	}

	@Test
	void studentCanReadTimetableForOwnGroupOnly() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String groupId = "TIMETABLE-" + suffix;
		String studentEmail = "timetable-student-" + suffix + "@example.test";
		String teacherEmail = "m.kaddour@univ-alger.dz";
		long studentUserId;
		long teacherUserId = findUserId(teacherEmail);
		long studentId;

		try (Connection connection = databaseConfig.openConnection()) {
			studentUserId = insertTestUser(connection, studentEmail, "student");
			try (PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO groups (id, name, academic_year) VALUES (?, ?, ?)")) {
				statement.setString(1, groupId);
				statement.setString(2, "Groupe emploi du temps");
				statement.setString(3, "2026-2027");
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO students (user_id, student_number, first_name, last_name, email, group_id)
					VALUES (?, ?, ?, ?, ?, ?)
					""")) {
				statement.setLong(1, studentUserId);
				statement.setString(2, "TIMETABLE-ETU-" + suffix);
				statement.setString(3, "Étudiant");
				statement.setString(4, "Planning");
				statement.setString(5, studentEmail);
				statement.setString(6, groupId);
				statement.executeUpdate();
			}
			try (Statement statement = connection.createStatement();
				 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
				assertTrue(resultSet.next());
				studentId = resultSet.getLong(1);
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO timetable_sessions (
						group_id, academic_year, day_of_week, subject, start_time, end_time,
						session_type, teacher_id, room, is_demo
					) VALUES (?, '2026-2027', 1, 'Matière de test', '08:00', '10:00', 'CM', NULL, 'Salle 12', 0)
					""")) {
				statement.setString(1, groupId);
				statement.executeUpdate();
			}
		}

		String studentToken = authTokenService.createToken(new AuthenticatedUser(
			studentUserId, studentEmail, "STUDENT"));
		String teacherToken = authTokenService.createToken(new AuthenticatedUser(
			teacherUserId, teacherEmail, "TEACHER"));

		try {
			mockMvc.perform(get("/api/v1/students/me/timetable"))
				.andExpect(status().isUnauthorized());
			mockMvc.perform(get("/api/v1/students/me/timetable")
					.header("Authorization", "Bearer " + teacherToken))
				.andExpect(status().isForbidden());
			mockMvc.perform(get("/api/v1/students/me/timetable")
					.header("Authorization", "Bearer " + studentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.groupId").value(groupId))
				.andExpect(jsonPath("$.academicYear").value("2026-2027"))
				.andExpect(jsonPath("$.demonstration").value(false))
				.andExpect(jsonPath("$.sessions.length()").value(1))
				.andExpect(jsonPath("$.sessions[0].dayOfWeek").value(1))
				.andExpect(jsonPath("$.sessions[0].dayName").value("Lundi"))
				.andExpect(jsonPath("$.sessions[0].subject").value("Matière de test"))
				.andExpect(jsonPath("$.sessions[0].sessionType").value("CM"))
				.andExpect(jsonPath("$.sessions[0].room").value("Salle 12"));
		} finally {
			try (Connection connection = databaseConfig.openConnection();
				 PreparedStatement schedule = connection.prepareStatement(
					"DELETE FROM timetable_sessions WHERE group_id = ?");
				 PreparedStatement student = connection.prepareStatement(
					"DELETE FROM students WHERE id = ?");
				 PreparedStatement group = connection.prepareStatement(
					"DELETE FROM groups WHERE id = ?");
				 PreparedStatement user = connection.prepareStatement(
					"DELETE FROM users WHERE id = ?")) {
				schedule.setString(1, groupId);
				schedule.executeUpdate();
				student.setLong(1, studentId);
				student.executeUpdate();
				group.setString(1, groupId);
				group.executeUpdate();
				user.setLong(1, studentUserId);
				user.executeUpdate();
			}
		}
	}

	@Test
	void corsAllowsLocalChromeAndCapacitorLoginOrigins() throws Exception {
		for (String origin : List.of("http://127.0.0.1:5187", "capacitor://localhost")) {
			mockMvc.perform(options("/api/v1/auth/login")
					.header("Origin", origin)
					.header("Access-Control-Request-Method", "POST")
					.header("Access-Control-Request-Headers", "content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", origin))
				.andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")));
		}

		mockMvc.perform(options("/api/v1/students/me")
				.header("Origin", "http://127.0.0.1:5187")
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "authorization"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5187"))
			.andExpect(header().string("Access-Control-Allow-Headers", containsString("authorization")));
	}

	@Test
	void roleAuthorizationScopesStudentsAbsencesAndJustifications() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String assignedGroupId = "SCOPE-A-" + suffix;
		String unassignedGroupId = "SCOPE-B-" + suffix;
		String studentEmail = "scope-student-" + suffix + "@example.test";
		String teacherEmail = "scope-teacher-" + suffix + "@example.test";
		long studentUserId;
		long teacherUserId;
		long otherStudentUserId;
		long assignedStudentId;
		long unassignedStudentId;
		long teacherId;
		long assignedAbsenceId;
		long unassignedAbsenceId;
		long assignedJustificationId;
		long unassignedJustificationId;

		try (Connection connection = databaseConfig.openConnection()) {
			studentUserId = insertTestUser(connection, studentEmail, "student");
			teacherUserId = insertTestUser(connection, teacherEmail, "teacher");
			try (PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO groups (id, name) VALUES (?, ?)")) {
				statement.setString(1, assignedGroupId);
				statement.setString(2, "Groupe attribué");
				statement.executeUpdate();
				statement.setString(1, unassignedGroupId);
				statement.setString(2, "Groupe non attribué");
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO users (email, password_hash, role) VALUES (?, ?, 'student')
					""")) {
				statement.setString(1, "other-student-" + suffix + "@example.test");
				statement.setString(2, "test-only-hash");
				statement.executeUpdate();
			}
			try (Statement statement = connection.createStatement();
				 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
				assertTrue(resultSet.next());
				otherStudentUserId = resultSet.getLong(1);
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO students (user_id, student_number, first_name, last_name, email, group_id)
					VALUES (?, ?, ?, ?, ?, ?)
					""")) {
				statement.setLong(1, studentUserId);
				statement.setString(2, "SCOPED-ETU-A-" + suffix);
				statement.setString(3, "Étudiant");
				statement.setString(4, "Attribué");
				statement.setString(5, studentEmail);
				statement.setString(6, assignedGroupId);
				statement.executeUpdate();
				statement.setLong(1, otherStudentUserId);
				statement.setString(2, "SCOPED-ETU-B-" + suffix);
				statement.setString(3, "Autre");
				statement.setString(4, "Étudiant");
				statement.setString(5, "other-student-" + suffix + "@example.test");
				statement.setString(6, unassignedGroupId);
				statement.executeUpdate();
			}
			try (Statement statement = connection.createStatement();
				 ResultSet resultSet = statement.executeQuery(
						"SELECT id FROM students WHERE user_id = " + studentUserId)) {
				assertTrue(resultSet.next());
				assignedStudentId = resultSet.getLong(1);
			}
			try (Statement statement = connection.createStatement();
				 ResultSet resultSet = statement.executeQuery(
						"SELECT id FROM students WHERE user_id = " + otherStudentUserId)) {
				assertTrue(resultSet.next());
				unassignedStudentId = resultSet.getLong(1);
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO teachers (user_id, teacher_identifier, first_name, last_name, email)
					VALUES (?, ?, ?, ?, ?)
					""")) {
				statement.setLong(1, teacherUserId);
				statement.setString(2, "SCOPED-ENS-" + suffix);
				statement.setString(3, "Enseignant");
				statement.setString(4, "Attribué");
				statement.setString(5, teacherEmail);
				statement.executeUpdate();
			}
			try (Statement statement = connection.createStatement();
				 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
				assertTrue(resultSet.next());
				teacherId = resultSet.getLong(1);
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO teacher_groups (teacher_id, group_id) VALUES (?, ?)")) {
				statement.setLong(1, teacherId);
				statement.setString(2, assignedGroupId);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO absences (student_id, teacher_id, absence_date, subject)
					VALUES (?, ?, ?, ?)
					""")) {
				statement.setLong(1, assignedStudentId);
				statement.setLong(2, teacherId);
				statement.setString(3, "2026-10-28");
				statement.setString(4, "Matière attribuée");
				statement.executeUpdate();
				try (Statement idStatement = connection.createStatement();
					 ResultSet resultSet = idStatement.executeQuery("SELECT last_insert_rowid()")) {
					assertTrue(resultSet.next());
					assignedAbsenceId = resultSet.getLong(1);
				}
				statement.setLong(1, unassignedStudentId);
				statement.setLong(2, teacherId);
				statement.setString(3, "2026-10-29");
				statement.setString(4, "Matière non attribuée");
				statement.executeUpdate();
				try (Statement idStatement = connection.createStatement();
					 ResultSet resultSet = idStatement.executeQuery("SELECT last_insert_rowid()")) {
					assertTrue(resultSet.next());
					unassignedAbsenceId = resultSet.getLong(1);
				}
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO justifications (absence_id, file_name) VALUES (?, ?)
					""")) {
				statement.setLong(1, assignedAbsenceId);
				statement.setString(2, "assigned.pdf");
				statement.executeUpdate();
				try (Statement idStatement = connection.createStatement();
					 ResultSet resultSet = idStatement.executeQuery("SELECT last_insert_rowid()")) {
					assertTrue(resultSet.next());
					assignedJustificationId = resultSet.getLong(1);
				}
				statement.setLong(1, unassignedAbsenceId);
				statement.setString(2, "unassigned.pdf");
				statement.executeUpdate();
				try (Statement idStatement = connection.createStatement();
					 ResultSet resultSet = idStatement.executeQuery("SELECT last_insert_rowid()")) {
					assertTrue(resultSet.next());
					unassignedJustificationId = resultSet.getLong(1);
				}
			}
		}

		String studentToken = authTokenService.createToken(new AuthenticatedUser(
			studentUserId, studentEmail, "STUDENT"));
		String otherStudentToken = authTokenService.createToken(new AuthenticatedUser(
			otherStudentUserId, "other-student-" + suffix + "@example.test", "STUDENT"));
		String teacherToken = authTokenService.createToken(new AuthenticatedUser(
			teacherUserId, teacherEmail, "TEACHER"));

		mockMvc.perform(get("/api/v1/students/me")
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(assignedStudentId));
		mockMvc.perform(get("/api/v1/students/" + unassignedStudentId)
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/absences")
				.param("studentId", String.valueOf(unassignedStudentId))
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/absences")
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].studentId").value(assignedStudentId));
		mockMvc.perform(get("/api/v1/absences/" + assignedAbsenceId)
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.studentId").value(assignedStudentId));
		mockMvc.perform(get("/api/v1/absences/" + unassignedAbsenceId)
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/justifications")
				.param("studentId", String.valueOf(unassignedStudentId))
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/justifications/" + unassignedJustificationId)
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/justifications")
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].studentId").value(assignedStudentId));
		mockMvc.perform(get("/api/v1/justifications/" + assignedJustificationId)
				.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.studentId").value(assignedStudentId));
		mockMvc.perform(post("/api/v1/justifications")
				.header("Authorization", "Bearer " + studentToken)
				.contentType("application/json")
				.content("""
					{"absenceId":%d,"fileName":"own.pdf"}
					""".formatted(assignedAbsenceId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.studentId").value(assignedStudentId));
		mockMvc.perform(post("/api/v1/justifications")
				.header("Authorization", "Bearer " + studentToken)
				.contentType("application/json")
				.content("""
					{"absenceId":%d,"fileName":"other.pdf"}
					""".formatted(unassignedAbsenceId)))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/absences")
				.header("Authorization", "Bearer " + studentToken)
				.contentType("application/json")
				.content("{}"))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/students")
				.header("Authorization", "Bearer " + teacherToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(assignedStudentId));
		mockMvc.perform(get("/api/v1/teachers/me")
				.header("Authorization", "Bearer " + teacherToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(teacherId))
			.andExpect(jsonPath("$.email").value(teacherEmail));
		mockMvc.perform(get("/api/v1/teachers/me/groups")
				.header("Authorization", "Bearer " + teacherToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(assignedGroupId));
		mockMvc.perform(get("/api/v1/groups")
				.header("Authorization", "Bearer " + teacherToken))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/students/" + unassignedStudentId)
				.header("Authorization", "Bearer " + teacherToken))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/absences")
				.header("Authorization", "Bearer " + teacherToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].studentId").value(assignedStudentId));
		mockMvc.perform(get("/api/v1/absences")
				.param("studentId", String.valueOf(unassignedStudentId))
				.header("Authorization", "Bearer " + teacherToken))
			.andExpect(status().isForbidden());
		String teacherAbsenceJson = mockMvc.perform(post("/api/v1/absences")
				.header("Authorization", "Bearer " + teacherToken)
				.contentType("application/json")
				.content("""
					{
					  "studentId": %d,
					  "teacherId": %d,
					  "absenceDate": "2026-10-30",
					  "subject": "Nouvelle matière",
					  "teacherName": "Identité falsifiée",
					  "status": "justified"
					}
					""".formatted(assignedStudentId, 999999999)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.teacherId").value(teacherId))
			.andExpect(jsonPath("$.teacherName").value("Enseignant Attribué"))
			.andExpect(jsonPath("$.status").value("unjustified"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long teacherCreatedAbsenceId = objectMapper.readTree(teacherAbsenceJson).get("id").asLong();
		mockMvc.perform(post("/api/v1/absences")
				.header("Authorization", "Bearer " + teacherToken)
				.contentType("application/json")
				.content("""
					{"studentId":%d,"absenceDate":"2026-10-30","subject":"Matière"}
					""".formatted(unassignedStudentId)))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/justifications")
				.header("Authorization", "Bearer " + teacherToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].studentId").value(assignedStudentId));
		mockMvc.perform(get("/api/v1/justifications/" + unassignedJustificationId)
				.header("Authorization", "Bearer " + teacherToken))
			.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/v1/justifications/" + assignedJustificationId + "/decision")
				.header("Authorization", "Bearer " + teacherToken)
				.contentType("application/json")
				.content("""
					{"status":"accepted","reviewedByUserId":%d}
					""".formatted(otherStudentUserId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewedByUserId").value(teacherUserId));
		mockMvc.perform(patch("/api/v1/justifications/" + unassignedJustificationId + "/decision")
				.header("Authorization", "Bearer " + teacherToken)
				.contentType("application/json")
				.content("""
					{"status":"accepted"}
					"""))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/absences/" + assignedAbsenceId)
				.header("Authorization", "Bearer " + teacherToken))
			.andExpect(status().isNoContent());
		assertTrue(authenticatedArraySize("/api/v1/students") >= 3);
		assertTrue(authenticatedArraySize("/api/v1/absences") >= 3);
		mockMvc.perform(get("/api/v1/absences/" + teacherCreatedAbsenceId)
				.header("Authorization", "Bearer " + otherStudentToken))
			.andExpect(status().isForbidden());

		try (Connection connection = databaseConfig.openConnection()) {
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM students WHERE id IN (?, ?)")) {
				statement.setLong(1, assignedStudentId);
				statement.setLong(2, unassignedStudentId);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM teachers WHERE id = ?")) {
				statement.setLong(1, teacherId);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM users WHERE id IN (?, ?, ?)")) {
				statement.setLong(1, studentUserId);
				statement.setLong(2, teacherUserId);
				statement.setLong(3, otherStudentUserId);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM groups WHERE id IN (?, ?)")) {
				statement.setString(1, assignedGroupId);
				statement.setString(2, unassignedGroupId);
				statement.executeUpdate();
			}
		}
	}

	@Test
	void createsTablesAndEnablesForeignKeysOnConnections() throws SQLException {
		try (Connection connection = databaseConfig.openConnection();
			 Statement statement = connection.createStatement()) {
			try (ResultSet foreignKeys = statement.executeQuery("PRAGMA foreign_keys")) {
				assertTrue(foreignKeys.next());
				assertEquals(1, foreignKeys.getInt(1));
			}

			Set<String> tableNames = new HashSet<>();
			try (ResultSet tables = statement.executeQuery(
					"SELECT name FROM sqlite_master WHERE type = 'table'")) {
				while (tables.next()) {
					tableNames.add(tables.getString("name"));
				}
			}

			assertTrue(tableNames.containsAll(Set.of(
				"users",
				"students",
				"teachers",
				"groups",
				"absences",
				"justifications",
				"teacher_groups"
			)));

			try (ResultSet foreignKeyDefinitions = statement.executeQuery("PRAGMA foreign_key_list(absences)")) {
				Set<String> referencedTables = new HashSet<>();
				while (foreignKeyDefinitions.next()) {
					referencedTables.add(foreignKeyDefinitions.getString("table"));
				}
				assertTrue(referencedTables.containsAll(Set.of("students", "teachers")));
			}
		}
	}

	@Test
	void studentCrudEndpointsValidateAndPersistData() throws Exception {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO groups (id, name) VALUES (?, ?)")) {
			statement.setString(1, "G1");
			statement.setString(2, "Groupe 1");
			statement.executeUpdate();
		}

		int initialStudentCount = authenticatedArraySize("/api/v1/students");
		assertTrue(initialStudentCount >= 1);

		String studentBody = """
			{
			  "studentNumber": "ETU-2026-0001",
			  "firstName": "Sara",
			  "lastName": "El Amrani",
			  "email": "sara@example.test",
			  "groupId": "G1",
			  "program": "Informatique L3",
			  "avatar": "SE",
			  "academicYear": "2026-2027"
			}
			""";
		String createdJson = performAuthenticated(post("/api/v1/students")
				.contentType("application/json")
				.content(studentBody))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", containsString("/api/v1/students/")))
			.andExpect(jsonPath("$.studentNumber").value("ETU-2026-0001"))
			.andExpect(jsonPath("$.firstName").value("Sara"))
			.andExpect(jsonPath("$.groupId").value("G1"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode created = objectMapper.readTree(createdJson);
		long studentId = created.get("id").asLong();

		performAuthenticated(get("/api/v1/students/{id}", studentId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("sara@example.test"));
		performAuthenticated(get("/api/v1/students"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(initialStudentCount + 1));

		String updatedBody = """
			{
			  "studentNumber": "ETU-2026-0001",
			  "firstName": "Sara",
			  "lastName": "El Amrani",
			  "email": "sara.updated@example.test",
			  "groupId": "G1",
			  "program": "Informatique L3",
			  "avatar": "SA",
			  "academicYear": "2026-2027"
			}
			""";
		performAuthenticated(put("/api/v1/students/{id}", studentId)
				.contentType("application/json")
				.content(updatedBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(studentId))
			.andExpect(jsonPath("$.email").value("sara.updated@example.test"))
			.andExpect(jsonPath("$.avatar").value("SA"));

		performAuthenticated(post("/api/v1/students")
				.contentType("application/json")
				.content(updatedBody.replace("ETU-2026-0001", "ETU-2026-0002")))
			.andExpect(status().isConflict());
		performAuthenticated(post("/api/v1/students")
				.contentType("application/json")
				.content(studentBody.replace("sara@example.test", "other@example.test")))
			.andExpect(status().isConflict());
		performAuthenticated(post("/api/v1/students")
				.contentType("application/json")
				.content(studentBody
					.replace("ETU-2026-0001", "ETU-2026-0003")
					.replace("sara@example.test", "third@example.test")
					.replace("\"G1\"", "\"UNKNOWN\"")))
			.andExpect(status().isBadRequest());
		performAuthenticated(post("/api/v1/students")
				.contentType("application/json")
				.content(studentBody.replace("\"Sara\"", "\" \"")))
			.andExpect(status().isBadRequest());

		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO absences (id, student_id, absence_date, subject)
				VALUES (?, ?, ?, ?)
				""")) {
			statement.setLong(1, 9001);
			statement.setLong(2, studentId);
			statement.setString(3, "2026-10-31");
			statement.setString(4, "Algorithmique");
			statement.executeUpdate();
		}

		performAuthenticated(delete("/api/v1/students/{id}", studentId))
			.andExpect(status().isNoContent());
		performAuthenticated(get("/api/v1/students/{id}", studentId))
			.andExpect(status().isNotFound());
		performAuthenticated(delete("/api/v1/students/{id}", studentId))
			.andExpect(status().isNotFound());

		try (Connection connection = databaseConfig.openConnection();
			 Statement statement = connection.createStatement();
			 ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM absences WHERE id = 9001")) {
			assertTrue(resultSet.next());
			assertEquals(0, resultSet.getInt(1), "Deleting a student cascades to its absences.");
		}
	}

	@Test
	void teacherCrudEndpointsManageTeacherGroupRelationsAndConstraints() throws Exception {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO groups (id, name) VALUES (?, ?)")) {
			for (String[] group : new String[][] {
					{ "TEACHER-G1", "Groupe enseignant 1" },
					{ "TEACHER-G2", "Groupe enseignant 2" } }) {
				statement.setString(1, group[0]);
				statement.setString(2, group[1]);
				statement.addBatch();
			}
			statement.executeBatch();
		}

		int initialTeacherCount = authenticatedArraySize("/api/v1/teachers");
		assertTrue(initialTeacherCount >= 1);

		String teacherBody = """
			{
			  "teacherIdentifier": "ENS-2026-0201",
			  "firstName": "Nadia",
			  "lastName": "Mansouri",
			  "email": "nadia@example.test",
			  "subject": "Génie logiciel",
			  "grade": "Maître de conférences",
			  "department": "Informatique",
			  "avatar": "NM",
			  "academicYear": "2026-2027",
			  "groupIds": ["TEACHER-G1", "TEACHER-G2"]
			}
			""";
		String createdJson = performAuthenticated(post("/api/v1/teachers")
				.contentType("application/json")
				.content(teacherBody))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", containsString("/api/v1/teachers/")))
			.andExpect(jsonPath("$.teacherIdentifier").value("ENS-2026-0201"))
			.andExpect(jsonPath("$.groupIds.length()").value(2))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long teacherId = objectMapper.readTree(createdJson).get("id").asLong();

		performAuthenticated(get("/api/v1/teachers/{id}", teacherId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("nadia@example.test"))
			.andExpect(jsonPath("$.groupIds[0]").value("TEACHER-G1"))
			.andExpect(jsonPath("$.groupIds[1]").value("TEACHER-G2"));
		performAuthenticated(get("/api/v1/teachers"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(initialTeacherCount + 1));

		String updatedBody = """
			{
			  "teacherIdentifier": "ENS-2026-0201",
			  "firstName": "Nadia",
			  "lastName": "Mansouri",
			  "email": "nadia.updated@example.test",
			  "subject": "Bases de données",
			  "grade": "Professeure",
			  "department": "Informatique",
			  "avatar": "NA",
			  "academicYear": "2026-2027",
			  "groupIds": ["TEACHER-G2"]
			}
			""";
		performAuthenticated(put("/api/v1/teachers/{id}", teacherId)
				.contentType("application/json")
				.content(updatedBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("nadia.updated@example.test"))
			.andExpect(jsonPath("$.subject").value("Bases de données"))
			.andExpect(jsonPath("$.groupIds.length()").value(1))
			.andExpect(jsonPath("$.groupIds[0]").value("TEACHER-G2"));

		performAuthenticated(post("/api/v1/teachers")
				.contentType("application/json")
				.content(updatedBody.replace("ENS-2026-0201", "ENS-2026-0202")))
			.andExpect(status().isConflict());
		performAuthenticated(post("/api/v1/teachers")
				.contentType("application/json")
				.content(teacherBody
					.replace("ENS-2026-0201", "ENS-2026-0203")
					.replace("nadia@example.test", "nadia.updated@example.test")))
			.andExpect(status().isConflict());
		performAuthenticated(post("/api/v1/teachers")
				.contentType("application/json")
				.content(teacherBody
					.replace("ENS-2026-0201", "ENS-2026-0204")
					.replace("nadia@example.test", "invalid-group@example.test")
					.replace("TEACHER-G2\"]", "UNKNOWN\"]")))
			.andExpect(status().isBadRequest());
		performAuthenticated(post("/api/v1/teachers")
				.contentType("application/json")
				.content(teacherBody
					.replace("ENS-2026-0201", "ENS-2026-0205")
					.replace("nadia@example.test", "duplicate-groups@example.test")
					.replace("[\"TEACHER-G1\", \"TEACHER-G2\"]", "[\"TEACHER-G1\", \"TEACHER-G1\"]")))
			.andExpect(status().isBadRequest());
		performAuthenticated(post("/api/v1/teachers")
				.contentType("application/json")
				.content(teacherBody
					.replace("ENS-2026-0201", "ENS-2026-0206")
					.replace("nadia@example.test", "invalid-email")
					.replace("[\"TEACHER-G1\", \"TEACHER-G2\"]", "[]")))
			.andExpect(status().isBadRequest());
		performAuthenticated(put("/api/v1/teachers/{id}", 999999)
				.contentType("application/json")
				.content(updatedBody))
			.andExpect(status().isNotFound());
		performAuthenticated(put("/api/v1/teachers/{id}", teacherId)
				.contentType("application/json")
				.content(updatedBody.replace("TEACHER-G2\"]", "UNKNOWN\"]")))
			.andExpect(status().isBadRequest());
		performAuthenticated(get("/api/v1/teachers/{id}", teacherId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("nadia.updated@example.test"))
			.andExpect(jsonPath("$.groupIds[0]").value("TEACHER-G2"));

		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(
					"SELECT COUNT(*) FROM teacher_groups WHERE teacher_id = ?")) {
			statement.setLong(1, teacherId);
			try (ResultSet resultSet = statement.executeQuery()) {
				assertTrue(resultSet.next());
				assertEquals(1, resultSet.getInt(1));
			}
		}

		performAuthenticated(delete("/api/v1/teachers/{id}", teacherId))
			.andExpect(status().isNoContent());
		performAuthenticated(get("/api/v1/teachers/{id}", teacherId))
			.andExpect(status().isNotFound());
		performAuthenticated(delete("/api/v1/teachers/{id}", teacherId))
			.andExpect(status().isNotFound());

		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(
					"SELECT COUNT(*) FROM teacher_groups WHERE teacher_id = ?")) {
			statement.setLong(1, teacherId);
			try (ResultSet resultSet = statement.executeQuery()) {
				assertTrue(resultSet.next());
				assertEquals(0, resultSet.getInt(1), "Deleting a teacher cascades to teacher_groups.");
			}
		}
	}

	@Test
	void groupCrudEndpointsRespectStudentAndTeacherReferences() throws Exception {
		performAuthenticated(get("/api/v1/groups/{id}", "MISSING-GROUP"))
			.andExpect(status().isNotFound());

		String groupBody = """
			{
			  "id": "api-group-1",
			  "name": "Groupe API initial",
			  "specialty": "Informatique L3",
			  "mainSubject": "Algorithmique",
			  "academicYear": "2026-2027"
			}
			""";
		performAuthenticated(post("/api/v1/groups")
				.contentType("application/json")
				.content(groupBody))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", containsString("/api/v1/groups/API-GROUP-1")))
			.andExpect(jsonPath("$.id").value("API-GROUP-1"))
			.andExpect(jsonPath("$.name").value("Groupe API initial"))
			.andExpect(jsonPath("$.studentCount").value(0))
			.andExpect(jsonPath("$.teacherIds.length()").value(0));

		performAuthenticated(post("/api/v1/groups")
				.contentType("application/json")
				.content(groupBody))
			.andExpect(status().isConflict());
		performAuthenticated(post("/api/v1/groups")
				.contentType("application/json")
				.content(groupBody.replace("\"name\": \"Groupe API initial\"", "\"name\": \" \"")))
			.andExpect(status().isBadRequest());
		performAuthenticated(post("/api/v1/groups")
				.contentType("application/json")
				.content(groupBody.replace("\"id\": \"api-group-1\",", "")))
			.andExpect(status().isBadRequest());

		performAuthenticated(get("/api/v1/groups"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == 'API-GROUP-1')]").exists());
		performAuthenticated(get("/api/v1/groups/{id}", "api-group-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("API-GROUP-1"));

		String updatedGroupBody = """
			{
			  "id": "IGNORED-PATH-ID",
			  "name": "Groupe API modifié",
			  "specialty": "Génie logiciel",
			  "mainSubject": "Bases de données",
			  "academicYear": "2027-2028"
			}
			""";
		performAuthenticated(put("/api/v1/groups/{id}", "API-GROUP-1")
				.contentType("application/json")
				.content(updatedGroupBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("API-GROUP-1"))
			.andExpect(jsonPath("$.name").value("Groupe API modifié"))
			.andExpect(jsonPath("$.specialty").value("Génie logiciel"));
		performAuthenticated(put("/api/v1/groups/{id}", "UNKNOWN-GROUP")
				.contentType("application/json")
				.content(updatedGroupBody))
			.andExpect(status().isNotFound());

		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement student = connection.prepareStatement("""
				INSERT INTO students (
					student_number, first_name, last_name, email, group_id
				) VALUES (?, ?, ?, ?, ?)
				""")) {
			student.setString(1, "GROUP-API-STUDENT");
			student.setString(2, "Test");
			student.setString(3, "Student");
			student.setString(4, "group-student@example.test");
			student.setString(5, "API-GROUP-1");
			student.executeUpdate();
		}
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement teacher = connection.prepareStatement("""
				INSERT INTO teachers (
					teacher_identifier, first_name, last_name, email
				) VALUES (?, ?, ?, ?)
				""")) {
			teacher.setString(1, "GROUP-API-TEACHER");
			teacher.setString(2, "Test");
			teacher.setString(3, "Teacher");
			teacher.setString(4, "group-teacher@example.test");
			teacher.executeUpdate();
		}
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement teacherGroup = connection.prepareStatement("""
				INSERT INTO teacher_groups (teacher_id, group_id)
				SELECT id, ? FROM teachers WHERE teacher_identifier = ?
				""")) {
			teacherGroup.setString(1, "API-GROUP-1");
			teacherGroup.setString(2, "GROUP-API-TEACHER");
			assertEquals(1, teacherGroup.executeUpdate());
		}

		performAuthenticated(get("/api/v1/groups/{id}", "API-GROUP-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.studentCount").value(1))
			.andExpect(jsonPath("$.teacherIds.length()").value(1));
		performAuthenticated(delete("/api/v1/groups/{id}", "API-GROUP-1"))
			.andExpect(status().isConflict());

		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM students WHERE student_number = ?")) {
			statement.setString(1, "GROUP-API-STUDENT");
			assertEquals(1, statement.executeUpdate());
		}
		performAuthenticated(delete("/api/v1/groups/{id}", "API-GROUP-1"))
			.andExpect(status().isConflict());

		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM teachers WHERE teacher_identifier = ?")) {
			statement.setString(1, "GROUP-API-TEACHER");
			assertEquals(1, statement.executeUpdate());
		}

		performAuthenticated(post("/api/v1/groups")
				.contentType("application/json")
				.content(groupBody.replace("api-group-1", "api-group-free")))
			.andExpect(status().isCreated());
		performAuthenticated(delete("/api/v1/groups/{id}", "API-GROUP-FREE"))
			.andExpect(status().isNoContent());
		performAuthenticated(get("/api/v1/groups/{id}", "API-GROUP-FREE"))
			.andExpect(status().isNotFound());
		performAuthenticated(delete("/api/v1/groups/{id}", "API-GROUP-FREE"))
			.andExpect(status().isNotFound());
	}

	@Test
	void absenceCrudEndpointsValidateReferencesDatesAndDuplicates() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String groupId = "ABS-" + suffix;
		long studentId;
		long teacherId;
		try (Connection connection = databaseConfig.openConnection()) {
			try (PreparedStatement group = connection.prepareStatement(
					"INSERT INTO groups (id, name) VALUES (?, ?)")) {
				group.setString(1, groupId);
				group.setString(2, "Groupe test absences");
				group.executeUpdate();
			}
			try (PreparedStatement student = connection.prepareStatement("""
					INSERT INTO students (student_number, first_name, last_name, email, group_id)
					VALUES (?, ?, ?, ?, ?)
					""")) {
				student.setString(1, "ABS-ETU-" + suffix);
				student.setString(2, "Test");
				student.setString(3, "Étudiant");
				student.setString(4, "absence-student-" + suffix + "@example.test");
				student.setString(5, groupId);
				student.executeUpdate();
			}
			try (Statement statement = connection.createStatement();
				 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
				assertTrue(resultSet.next());
				studentId = resultSet.getLong(1);
			}
			try (PreparedStatement teacher = connection.prepareStatement("""
					INSERT INTO teachers (teacher_identifier, first_name, last_name, email)
					VALUES (?, ?, ?, ?)
					""")) {
				teacher.setString(1, "ABS-ENS-" + suffix);
				teacher.setString(2, "Test");
				teacher.setString(3, "Enseignant");
				teacher.setString(4, "absence-teacher-" + suffix + "@example.test");
				teacher.executeUpdate();
			}
			try (Statement statement = connection.createStatement();
				 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
				assertTrue(resultSet.next());
				teacherId = resultSet.getLong(1);
			}
		}

		int initialAbsenceCount = authenticatedArraySize("/api/v1/absences");
		performAuthenticated(get("/api/v1/absences").param("studentId", "0"))
			.andExpect(status().isBadRequest());
		performAuthenticated(get("/api/v1/absences").param("studentId", "999999999"))
			.andExpect(status().isNotFound());

		String absenceBody = """
			{
			  "studentId": %d,
			  "teacherId": %d,
			  "absenceDate": "2026-10-31",
			  "subject": "Algorithmique avancée",
			  "teacherName": "Dr. Test",
			  "sessionType": "CM",
			  "startTime": "08:00",
			  "endTime": "10:00",
			  "status": "unjustified"
			}
			""".formatted(studentId, teacherId);
		String createdJson = performAuthenticated(post("/api/v1/absences")
				.contentType("application/json")
				.content(absenceBody))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", containsString("/api/v1/absences/")))
			.andExpect(jsonPath("$.studentId").value(studentId))
			.andExpect(jsonPath("$.teacherId").value(teacherId))
			.andExpect(jsonPath("$.sessionLabel").value("CM 08:00–10:00"))
			.andExpect(jsonPath("$.status").value("unjustified"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long absenceId = objectMapper.readTree(createdJson).get("id").asLong();

		performAuthenticated(post("/api/v1/absences")
				.contentType("application/json")
				.content(absenceBody))
			.andExpect(status().isConflict());
		performAuthenticated(get("/api/v1/absences"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(initialAbsenceCount + 1));
		performAuthenticated(get("/api/v1/absences").param("studentId", String.valueOf(studentId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(absenceId));
		performAuthenticated(get("/api/v1/absences/{id}", absenceId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.subject").value("Algorithmique avancée"));
		performAuthenticated(get("/api/v1/absences/{id}", 999999999))
			.andExpect(status().isNotFound());

		String updatedBody = """
			{
			  "studentId": %d,
			  "teacherId": null,
			  "absenceDate": "2026-11-01",
			  "subject": "Réseaux informatiques",
			  "teacherName": null,
			  "sessionLabel": "TD 10:00–12:00",
			  "sessionType": "TD",
			  "startTime": "10:00",
			  "endTime": "12:00",
			  "status": "justified"
			}
			""".formatted(studentId);
		performAuthenticated(put("/api/v1/absences/{id}", absenceId)
				.contentType("application/json")
				.content(updatedBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.teacherId").doesNotExist())
			.andExpect(jsonPath("$.absenceDate").value("2026-11-01"))
			.andExpect(jsonPath("$.status").value("justified"));

		String invalidStudentBody = absenceBody
			.replace("\"studentId\": " + studentId, "\"studentId\": 999999999")
			.replace("2026-10-31", "2026-11-02");
		performAuthenticated(post("/api/v1/absences")
				.contentType("application/json")
				.content(invalidStudentBody))
			.andExpect(status().isBadRequest());
		String invalidTeacherBody = absenceBody
			.replace("\"teacherId\": " + teacherId, "\"teacherId\": 999999999")
			.replace("2026-10-31", "2026-11-03");
		performAuthenticated(post("/api/v1/absences")
				.contentType("application/json")
				.content(invalidTeacherBody))
			.andExpect(status().isBadRequest());
		performAuthenticated(post("/api/v1/absences")
				.contentType("application/json")
				.content(absenceBody.replace("2026-10-31", "31/10/2026")))
			.andExpect(status().isBadRequest());
		performAuthenticated(post("/api/v1/absences")
				.contentType("application/json")
				.content(absenceBody
					.replace("2026-10-31", "2026-11-04")
					.replace("unjustified", "pending")))
			.andExpect(status().isBadRequest());
		performAuthenticated(post("/api/v1/absences")
				.contentType("application/json")
				.content(absenceBody
					.replace("2026-10-31", "2026-11-05")
					.replace("08:00", "12:00")
					.replace("10:00", "10:00")))
			.andExpect(status().isBadRequest());

		String secondAbsenceBody = absenceBody
			.replace("2026-10-31", "2026-11-06")
			.replace("Algorithmique avancée", "Bases de données");
		String secondCreated = performAuthenticated(post("/api/v1/absences")
				.contentType("application/json")
				.content(secondAbsenceBody))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long secondAbsenceId = objectMapper.readTree(secondCreated).get("id").asLong();
		String duplicateUpdateBody = secondAbsenceBody.replace("2026-11-06", "2026-11-01")
			.replace("Bases de données", "Réseaux informatiques")
			.replace("\"sessionType\": \"CM\"", "\"sessionType\": \"TD\"");
		performAuthenticated(put("/api/v1/absences/{id}", secondAbsenceId)
				.contentType("application/json")
				.content(duplicateUpdateBody))
			.andExpect(status().isConflict());
		performAuthenticated(get("/api/v1/absences/{id}", secondAbsenceId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.absenceDate").value("2026-11-06"));

		performAuthenticated(delete("/api/v1/absences/{id}", absenceId))
			.andExpect(status().isNoContent());
		performAuthenticated(get("/api/v1/absences/{id}", absenceId))
			.andExpect(status().isNotFound());
		performAuthenticated(delete("/api/v1/absences/{id}", absenceId))
			.andExpect(status().isNotFound());
		performAuthenticated(delete("/api/v1/absences/{id}", secondAbsenceId))
			.andExpect(status().isNoContent());

		try (Connection connection = databaseConfig.openConnection()) {
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM students WHERE id = ?")) {
				statement.setLong(1, studentId);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM teachers WHERE id = ?")) {
				statement.setLong(1, teacherId);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM groups WHERE id = ?")) {
				statement.setString(1, groupId);
				statement.executeUpdate();
			}
		}
	}

	@Test
	void teachersCanUpdateOnlyAbsencesAndStudentsInTheirAssignedGroups() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String assignedGroup = "UPDATE-G1-" + suffix;
		String otherGroup = "UPDATE-G2-" + suffix;
		String teacherEmail = "update-teacher-" + suffix + "@example.test";
		String noGroupsEmail = "update-no-groups-" + suffix + "@example.test";
		String noProfileEmail = "update-no-profile-" + suffix + "@example.test";
		String assignedStudentEmail = "update-student-" + suffix + "@example.test";
		String otherStudentEmail = "update-outside-" + suffix + "@example.test";
		long teacherUserId;
		long noGroupsUserId;
		long noProfileUserId;
		long assignedStudentUserId;
		long otherStudentUserId;
		long teacherId;
		long assignedStudentId;
		long otherStudentId;
		long assignedAbsenceId;
		long otherAbsenceId;

		try (Connection connection = databaseConfig.openConnection()) {
			try (PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO groups (id, name) VALUES (?, ?), (?, ?)")) {
				statement.setString(1, assignedGroup);
				statement.setString(2, "Groupe attribué");
				statement.setString(3, otherGroup);
				statement.setString(4, "Groupe non attribué");
				statement.executeUpdate();
			}

			teacherUserId = insertTestUser(connection, teacherEmail, "teacher");
			noGroupsUserId = insertTestUser(connection, noGroupsEmail, "teacher");
			noProfileUserId = insertTestUser(connection, noProfileEmail, "teacher");
			assignedStudentUserId = insertTestUser(connection, assignedStudentEmail, "student");
			otherStudentUserId = insertTestUser(connection, otherStudentEmail, "student");
			teacherId = insertTestTeacher(connection, teacherUserId, suffix, "update");
			insertTestTeacher(connection, noGroupsUserId, suffix, "no-groups");
			assignedStudentId = insertTestStudent(connection, assignedStudentUserId, assignedGroup, suffix, "assigned");
			otherStudentId = insertTestStudent(connection, otherStudentUserId, otherGroup, suffix, "outside");

			try (PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO teacher_groups (teacher_id, group_id) VALUES (?, ?)")) {
				statement.setLong(1, teacherId);
				statement.setString(2, assignedGroup);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO absences (
						student_id, teacher_id, absence_date, subject, teacher_name,
						session_type, start_time, end_time, status
					) VALUES (?, ?, '2026-10-30', 'Algorithmique', ?, 'CM', '08:00', '10:00', 'unjustified')
					""")) {
				statement.setLong(1, assignedStudentId);
				statement.setLong(2, teacherId);
				statement.setString(3, "Initial Teacher");
				statement.executeUpdate();
				assignedAbsenceId = lastInsertedId(connection);

				statement.setLong(1, otherStudentId);
				statement.setNull(2, java.sql.Types.BIGINT);
				statement.setString(3, "Other Teacher");
				statement.executeUpdate();
				otherAbsenceId = lastInsertedId(connection);
			}
		}

		String updateBody = """
			{
			  "studentId": %d,
			  "teacherId": 999999999,
			  "absenceDate": "2026-11-01",
			  "subject": "Réseaux informatiques",
			  "teacherName": "Enseignant falsifié",
			  "sessionLabel": "TD 10:00–12:00",
			  "sessionType": "TD",
			  "startTime": "10:00",
			  "endTime": "12:00",
			  "status": "justified"
			}
			""".formatted(assignedStudentId);

		performAs(
				put("/api/v1/absences/{id}", assignedAbsenceId)
					.contentType("application/json")
					.content(updateBody),
				teacherUserId,
				teacherEmail,
				"TEACHER")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.studentId").value(assignedStudentId))
			.andExpect(jsonPath("$.teacherId").value(teacherId))
			.andExpect(jsonPath("$.teacherName").value("Test update"))
			.andExpect(jsonPath("$.absenceDate").value("2026-11-01"))
			.andExpect(jsonPath("$.status").value("unjustified"));

		String moveOutsideGroup = updateBody.replace(
			"\"studentId\": " + assignedStudentId,
			"\"studentId\": " + otherStudentId
		);
		performAs(
				put("/api/v1/absences/{id}", assignedAbsenceId)
					.contentType("application/json")
					.content(moveOutsideGroup),
				teacherUserId,
				teacherEmail,
				"TEACHER")
			.andExpect(status().isForbidden());

		performAs(
				put("/api/v1/absences/{id}", otherAbsenceId)
					.contentType("application/json")
					.content(updateBody),
				teacherUserId,
				teacherEmail,
				"TEACHER")
			.andExpect(status().isForbidden());

		performAs(
				put("/api/v1/absences/{id}", assignedAbsenceId)
					.contentType("application/json")
					.content(updateBody),
				noGroupsUserId,
				noGroupsEmail,
				"TEACHER")
			.andExpect(status().isForbidden());

		performAs(
				put("/api/v1/absences/{id}", assignedAbsenceId)
					.contentType("application/json")
					.content(updateBody),
				assignedStudentUserId,
				assignedStudentEmail,
				"STUDENT")
			.andExpect(status().isForbidden());

		performAuthenticated(get("/api/v1/absences/{id}", assignedAbsenceId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.studentId").value(assignedStudentId))
			.andExpect(jsonPath("$.teacherId").value(teacherId))
			.andExpect(jsonPath("$.status").value("unjustified"));

		performAs(
				get("/api/v1/teachers/me/groups"),
				noGroupsUserId,
				noGroupsEmail,
				"TEACHER")
			.andExpect(status().isOk())
			.andExpect(content().json("[]"));

		performAs(
				get("/api/v1/teachers/me"),
				noProfileUserId,
				noProfileEmail,
				"TEACHER")
			.andExpect(status().isForbidden())
			.andExpect(result -> {
				assertTrue(result.getResolvedException() instanceof ResponseStatusException);
				assertEquals(
					"Ce compte enseignant n’est pas encore associé à un profil enseignant. Contactez l’administration.",
					((ResponseStatusException) result.getResolvedException()).getReason()
				);
			});

		performAs(
				get("/api/v1/students/{id}/attendance-stats", assignedStudentId),
				teacherUserId,
				teacherEmail,
				"TEACHER")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.plannedSessionsCount").value(0));
		performAs(
				get("/api/v1/students/{id}/attendance-stats", otherStudentId),
				teacherUserId,
				teacherEmail,
				"TEACHER")
			.andExpect(status().isForbidden());

		performAs(
				delete("/api/v1/absences/{id}", assignedAbsenceId),
				teacherUserId,
				teacherEmail,
				"TEACHER")
			.andExpect(status().isNoContent());
		performAs(
				delete("/api/v1/absences/{id}", otherAbsenceId),
				teacherUserId,
				teacherEmail,
				"TEACHER")
			.andExpect(status().isForbidden());
	}

	@Test
	void justificationEndpointsManageMetadataReviewAndPendingOnlyEdits() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String groupId = "JUST-" + suffix;
		long studentId;
		long teacherId;
		long reviewerId;
		long[] absenceIds = new long[3];

			try (Connection connection = databaseConfig.openConnection()) {
				try (PreparedStatement statement = connection.prepareStatement(
						"INSERT INTO groups (id, name) VALUES (?, ?)")) {
					statement.setString(1, groupId);
					statement.setString(2, "Groupe justificatifs");
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO students (student_number, first_name, last_name, email, group_id)
						VALUES (?, ?, ?, ?, ?)
						""")) {
					statement.setString(1, "JUST-ETU-" + suffix);
					statement.setString(2, "Test");
					statement.setString(3, "Étudiant");
					statement.setString(4, "just-student-" + suffix + "@example.test");
					statement.setString(5, groupId);
					statement.executeUpdate();
				}
				try (Statement statement = connection.createStatement();
					 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
					assertTrue(resultSet.next());
					studentId = resultSet.getLong(1);
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO teachers (teacher_identifier, first_name, last_name, email)
						VALUES (?, ?, ?, ?)
						""")) {
					statement.setString(1, "JUST-ENS-" + suffix);
					statement.setString(2, "Test");
					statement.setString(3, "Enseignant");
					statement.setString(4, "just-teacher-" + suffix + "@example.test");
					statement.executeUpdate();
				}
				try (Statement statement = connection.createStatement();
					 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
					assertTrue(resultSet.next());
					teacherId = resultSet.getLong(1);
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO users (email, password_hash, role)
						VALUES (?, ?, 'administrator')
						""")) {
					statement.setString(1, "just-reviewer-" + suffix + "@example.test");
					statement.setString(2, "test-password-hash");
					statement.executeUpdate();
				}
				try (Statement statement = connection.createStatement();
					 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
					assertTrue(resultSet.next());
					reviewerId = resultSet.getLong(1);
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO absences (
							student_id, teacher_id, absence_date, subject, status
						) VALUES (?, ?, ?, ?, 'unjustified')
						""")) {
					for (int index = 0; index < absenceIds.length; index++) {
						statement.setLong(1, studentId);
						statement.setLong(2, teacherId);
						statement.setString(3, "2026-10-" + (20 + index));
						statement.setString(4, "Matière test " + index);
						statement.executeUpdate();
						try (Statement idStatement = connection.createStatement();
							 ResultSet resultSet = idStatement.executeQuery("SELECT last_insert_rowid()")) {
							assertTrue(resultSet.next());
							absenceIds[index] = resultSet.getLong(1);
						}
					}
				}
			}

			int initialJustificationCount = authenticatedArraySize("/api/v1/justifications");
			performAuthenticated(get("/api/v1/justifications").param("studentId", "0"))
				.andExpect(status().isBadRequest());
			performAuthenticated(get("/api/v1/justifications").param("absenceId", "999999999"))
				.andExpect(status().isNotFound());
			performAuthenticated(get("/api/v1/justifications").param("studentId", "999999999"))
				.andExpect(status().isNotFound());

			String firstRequest = """
				{
				  "absenceId": %d,
				  "fileName": "certificat-medical.pdf",
				  "filePath": "metadata-only/certificat-medical.pdf"
				}
				""".formatted(absenceIds[0]);
			String firstCreated = performAuthenticated(post("/api/v1/justifications")
					.contentType("application/json")
					.content(firstRequest))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", containsString("/api/v1/justifications/")))
				.andExpect(jsonPath("$.absenceId").value(absenceIds[0]))
				.andExpect(jsonPath("$.studentId").value(studentId))
				.andExpect(jsonPath("$.fileName").value("certificat-medical.pdf"))
				.andExpect(jsonPath("$.filePath").value("metadata-only/certificat-medical.pdf"))
				.andExpect(jsonPath("$.status").value("pending"))
				.andExpect(jsonPath("$.reviewedAt").doesNotExist())
				.andReturn()
				.getResponse()
				.getContentAsString();
			long firstId = objectMapper.readTree(firstCreated).get("id").asLong();

			String secondRequest = """
				{
				  "absenceId": %d,
				  "fileName": "justificatif-transport.pdf"
				}
				""".formatted(absenceIds[1]);
			String secondCreated = performAuthenticated(post("/api/v1/justifications")
					.contentType("application/json")
					.content(secondRequest))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.filePath").doesNotExist())
				.andReturn()
				.getResponse()
				.getContentAsString();
			long secondId = objectMapper.readTree(secondCreated).get("id").asLong();

			String thirdRequest = """
				{
				  "absenceId": %d,
				  "fileName": "ordonnance.pdf"
				}
				""".formatted(absenceIds[2]);
			String thirdCreated = performAuthenticated(post("/api/v1/justifications")
					.contentType("application/json")
					.content(thirdRequest))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
			long thirdId = objectMapper.readTree(thirdCreated).get("id").asLong();
			assertEquals(initialJustificationCount + 3, authenticatedArraySize("/api/v1/justifications"));

			performAuthenticated(post("/api/v1/justifications")
					.contentType("application/json")
					.content(firstRequest.replace(
						String.valueOf(absenceIds[0]),
						String.valueOf(999999999))))
				.andExpect(status().isBadRequest());
			performAuthenticated(post("/api/v1/justifications")
					.contentType("application/json")
					.content(firstRequest.replace("\"fileName\": \"certificat-medical.pdf\"", "\"fileName\": \" \"" )))
				.andExpect(status().isBadRequest());
			performAuthenticated(get("/api/v1/justifications/{id}", firstId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.studentId").value(studentId));
			performAuthenticated(get("/api/v1/justifications/{id}", 999999999))
				.andExpect(status().isNotFound());
			performAuthenticated(get("/api/v1/justifications").param("studentId", String.valueOf(studentId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3));
			performAuthenticated(get("/api/v1/justifications").param("absenceId", String.valueOf(absenceIds[0])))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(firstId));

			performAuthenticated(patch("/api/v1/justifications/{id}/decision", firstId)
					.contentType("application/json")
					.content("""
						{"status":"accepted","reviewedByUserId":%d}
						""".formatted(reviewerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("accepted"))
				.andExpect(jsonPath("$.reviewedByUserId").value(findUserId("scolarite@univ-alger.dz")))
				.andExpect(jsonPath("$.reviewedAt").isNotEmpty());
			try (Connection connection = databaseConfig.openConnection();
				 PreparedStatement statement = connection.prepareStatement(
						"SELECT status FROM absences WHERE id = ?")) {
				statement.setLong(1, absenceIds[0]);
				try (ResultSet resultSet = statement.executeQuery()) {
					assertTrue(resultSet.next());
					assertEquals("justified", resultSet.getString(1));
				}
			}
			performAuthenticated(patch("/api/v1/justifications/{id}/decision", firstId)
					.contentType("application/json")
					.content("{\"status\":\"rejected\"}"))
				.andExpect(status().isConflict());
			performAuthenticated(put("/api/v1/justifications/{id}", firstId)
					.contentType("application/json")
					.content(firstRequest))
				.andExpect(status().isConflict());
			performAuthenticated(delete("/api/v1/justifications/{id}", firstId))
				.andExpect(status().isConflict());

			String updatePending = """
				{
				  "absenceId": %d,
				  "fileName": "updated-transport.pdf",
				  "filePath": "metadata-only/updated-transport.pdf"
				}
				""".formatted(absenceIds[1]);
			performAuthenticated(put("/api/v1/justifications/{id}", secondId)
					.contentType("application/json")
					.content(updatePending))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fileName").value("updated-transport.pdf"))
				.andExpect(jsonPath("$.status").value("pending"));
			performAuthenticated(patch("/api/v1/justifications/{id}/decision", secondId)
					.contentType("application/json")
					.content("{\"status\":\"invalid\"}"))
				.andExpect(status().isBadRequest());
			performAuthenticated(get("/api/v1/justifications/{id}", secondId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("pending"));
			performAuthenticated(delete("/api/v1/justifications/{id}", secondId))
				.andExpect(status().isNoContent());
			performAuthenticated(get("/api/v1/justifications/{id}", secondId))
				.andExpect(status().isNotFound());

			performAuthenticated(patch("/api/v1/justifications/{id}/decision", thirdId)
					.contentType("application/json")
					.content("{\"status\":\"rejected\",\"reviewedByUserId\":999999999}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("rejected"))
				.andExpect(jsonPath("$.reviewedByUserId").value(findUserId("scolarite@univ-alger.dz")));
			try (Connection connection = databaseConfig.openConnection();
				 PreparedStatement statement = connection.prepareStatement(
						"SELECT status FROM absences WHERE id = ?")) {
				statement.setLong(1, absenceIds[2]);
				try (ResultSet resultSet = statement.executeQuery()) {
					assertTrue(resultSet.next());
					assertEquals("unjustified", resultSet.getString(1));
				}
			}
			performAuthenticated(put("/api/v1/justifications/{id}", thirdId)
					.contentType("application/json")
					.content(thirdRequest))
				.andExpect(status().isConflict());
			performAuthenticated(delete("/api/v1/justifications/{id}", thirdId))
				.andExpect(status().isConflict());

			try (Connection connection = databaseConfig.openConnection()) {
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM students WHERE id = ?")) {
					statement.setLong(1, studentId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM teachers WHERE id = ?")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM users WHERE id = ?")) {
					statement.setLong(1, reviewerId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM groups WHERE id = ?")) {
					statement.setString(1, groupId);
					statement.executeUpdate();
				}
			}
		}
	}
