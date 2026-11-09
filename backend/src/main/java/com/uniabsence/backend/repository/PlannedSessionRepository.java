package com.uniabsence.backend.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.uniabsence.backend.config.DatabaseConfig;
import com.uniabsence.backend.dto.PlannedSessionRequest;
import com.uniabsence.backend.dto.PlannedSessionResponse;

@Repository
public class PlannedSessionRepository {

	private static final String SELECT_COLUMNS = """
		SELECT ps.id, ps.group_id, ps.academic_year, ps.session_date, ps.start_time, ps.end_time,
		       ps.subject, ps.session_type, ps.teacher_id, ps.room, ps.status, ps.created_at,
		       CASE WHEN t.id IS NULL THEN NULL
		            ELSE TRIM(t.first_name || ' ' || t.last_name)
		       END AS teacher_name
		FROM planned_sessions ps
		LEFT JOIN teachers t ON t.id = ps.teacher_id
		""";

	private final DatabaseConfig databaseConfig;

	public PlannedSessionRepository(DatabaseConfig databaseConfig) {
		this.databaseConfig = databaseConfig;
	}

	public List<PlannedSessionResponse> findByGroupIdAndPeriod(
			String groupId,
			String academicYear,
			String startDate,
			String endDate) throws SQLException {
		String sql = SELECT_COLUMNS + """
			WHERE ps.group_id = ?
			  AND ps.academic_year = ?
			  AND ps.session_date BETWEEN ? AND ?
			ORDER BY ps.session_date, ps.start_time, ps.id
			""";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, groupId);
			statement.setString(2, academicYear);
			statement.setString(3, startDate);
			statement.setString(4, endDate);
			try (ResultSet resultSet = statement.executeQuery()) {
				List<PlannedSessionResponse> sessions = new ArrayList<>();
				while (resultSet.next()) {
					sessions.add(mapSession(resultSet));
				}
				return sessions;
			}
		}
	}

	public List<PlannedSessionResponse> findAll() throws SQLException {
		String sql = SELECT_COLUMNS + " ORDER BY ps.session_date DESC, ps.start_time, ps.id";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql);
			 ResultSet resultSet = statement.executeQuery()) {
			List<PlannedSessionResponse> sessions = new ArrayList<>();
			while (resultSet.next()) {
				sessions.add(mapSession(resultSet));
			}
			return sessions;
		}
	}

	public Optional<PlannedSessionResponse> findById(long id) throws SQLException {
		String sql = SELECT_COLUMNS + " WHERE ps.id = ?";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, id);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() ? Optional.of(mapSession(resultSet)) : Optional.empty();
			}
		}
	}

	public int countDistinctPlannedSessions(
			String groupId,
			String academicYear,
			String startDate,
			String endDate) throws SQLException {
		String sql = """
			SELECT COUNT(DISTINCT session_date || '#' || start_time || '#' || session_type)
			FROM planned_sessions
			WHERE group_id = ?
			  AND academic_year = ?
			  AND session_date BETWEEN ? AND ?
			  AND status != 'cancelled'
			""";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, groupId);
			statement.setString(2, academicYear);
			statement.setString(3, startDate);
			statement.setString(4, endDate);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() ? resultSet.getInt(1) : 0;
			}
		}
	}

	public int countDistinctMissedSessions(
			long studentId,
			String startDate,
			String endDate) throws SQLException {
		String sql = """
			SELECT COUNT(DISTINCT absence_date || '#' || COALESCE(start_time, '') || '#' || COALESCE(session_type, ''))
			FROM absences
			WHERE student_id = ?
			  AND absence_date BETWEEN ? AND ?
			""";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, studentId);
			statement.setString(2, startDate);
			statement.setString(3, endDate);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() ? resultSet.getInt(1) : 0;
			}
		}
	}

	public AbsenceCounts countAbsences(long studentId, String startDate, String endDate) throws SQLException {
		String sql = """
			WITH session_status AS (
				SELECT
					a.absence_date,
					COALESCE(a.start_time, '') AS start_time,
					COALESCE(a.session_type, '') AS session_type,
					MAX(CASE WHEN a.status = 'justified' OR j.status = 'accepted' THEN 1 ELSE 0 END) AS is_justified
				FROM absences a
				LEFT JOIN justifications j ON j.absence_id = a.id
				WHERE a.student_id = ?
				  AND a.absence_date BETWEEN ? AND ?
				GROUP BY a.absence_date, COALESCE(a.start_time, ''), COALESCE(a.session_type, '')
			)
			SELECT
				COUNT(*) AS total_count,
				COALESCE(SUM(is_justified), 0) AS justified_count,
				COUNT(*) - COALESCE(SUM(is_justified), 0) AS unjustified_count
			FROM session_status
			""";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, studentId);
			statement.setString(2, startDate);
			statement.setString(3, endDate);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next()) {
					int total = resultSet.getInt("total_count");
					int justified = resultSet.getInt("justified_count");
					int unjustified = resultSet.getInt("unjustified_count");
					return new AbsenceCounts(total, justified, unjustified);
				}
				return new AbsenceCounts(0, 0, 0);
			}
		}
	}

	public boolean hasPlannedSessions(String groupId, String academicYear) throws SQLException {
		String sql = "SELECT 1 FROM planned_sessions WHERE group_id = ? AND academic_year = ? LIMIT 1";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, groupId);
			statement.setString(2, academicYear);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	public PlannedSessionResponse create(PlannedSessionRequest request) throws SQLException {
		String sql = """
			INSERT INTO planned_sessions (
				group_id, academic_year, session_date, start_time, end_time,
				subject, session_type, teacher_id, room, status
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";
		try (Connection connection = databaseConfig.openConnection()) {
			connection.setAutoCommit(false);
			try (PreparedStatement statement = connection.prepareStatement(sql)) {
				statement.setString(1, request.groupId());
				statement.setString(2, request.academicYear());
				statement.setString(3, request.sessionDate());
				statement.setString(4, request.startTime());
				statement.setString(5, request.endTime());
				statement.setString(6, request.subject());
				statement.setString(7, request.sessionType());
				if (request.teacherId() == null) {
					statement.setNull(8, Types.INTEGER);
				} else {
					statement.setLong(8, request.teacherId());
				}
				statement.setString(9, request.room());
				statement.setString(10, request.status() == null || request.status().isBlank() ? "scheduled" : request.status());
				statement.executeUpdate();

				long id;
				try (Statement idStatement = connection.createStatement();
					 ResultSet resultSet = idStatement.executeQuery("SELECT last_insert_rowid()")) {
					if (!resultSet.next()) {
						throw new SQLException("SQLite n'a pas retourné l'identifiant inséré.");
					}
					id = resultSet.getLong(1);
				}
				connection.commit();
				return findById(id).orElseThrow(() -> new SQLException("Séance créée introuvable."));
			} catch (SQLException exception) {
				connection.rollback();
				throw exception;
			}
		}
	}

	public boolean delete(long id) throws SQLException {
		String sql = "DELETE FROM planned_sessions WHERE id = ?";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, id);
			return statement.executeUpdate() > 0;
		}
	}

	private PlannedSessionResponse mapSession(ResultSet resultSet) throws SQLException {
		Long teacherId = resultSet.getLong("teacher_id");
		if (resultSet.wasNull()) {
			teacherId = null;
		}
		return new PlannedSessionResponse(
			resultSet.getLong("id"),
			resultSet.getString("group_id"),
			resultSet.getString("academic_year"),
			resultSet.getString("session_date"),
			resultSet.getString("start_time"),
			resultSet.getString("end_time"),
			resultSet.getString("subject"),
			resultSet.getString("session_type"),
			teacherId,
			resultSet.getString("teacher_name"),
			resultSet.getString("room"),
			resultSet.getString("status"),
			resultSet.getString("created_at")
		);
	}

	public record AbsenceCounts(int total, int justified, int unjustified) {
	}
}
