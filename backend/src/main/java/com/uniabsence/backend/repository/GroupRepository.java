package com.uniabsence.backend.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.uniabsence.backend.config.DatabaseConfig;
import com.uniabsence.backend.dto.GroupRequest;
import com.uniabsence.backend.dto.GroupResponse;

@Repository
public class GroupRepository {

	private static final String SELECT_COLUMNS = """
		SELECT id, name, specialty, main_subject, academic_year, created_at
		FROM groups
		""";

	private final DatabaseConfig databaseConfig;

	public GroupRepository(DatabaseConfig databaseConfig) {
		this.databaseConfig = databaseConfig;
	}

	public List<GroupResponse> findAll() throws SQLException {
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS + " ORDER BY id");
			 ResultSet resultSet = statement.executeQuery()) {
			List<GroupResponse> groups = new ArrayList<>();
			while (resultSet.next()) {
				groups.add(mapGroup(connection, resultSet));
			}
			return groups;
		}
	}

	public List<GroupResponse> findForTeacherUserId(long userId) throws SQLException {
		String sql = SELECT_COLUMNS + """
			WHERE EXISTS (
				SELECT 1
				FROM teacher_groups tg
				JOIN teachers t ON t.id = tg.teacher_id
				WHERE tg.group_id = groups.id AND t.user_id = ?
			)
			ORDER BY id
			""";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, userId);
			try (ResultSet resultSet = statement.executeQuery()) {
				List<GroupResponse> groups = new ArrayList<>();
				while (resultSet.next()) {
					groups.add(mapGroup(connection, resultSet));
				}
				return groups;
			}
		}
	}

	public Optional<GroupResponse> findById(String id) throws SQLException {
		try (Connection connection = databaseConfig.openConnection()) {
			return findById(connection, id);
		}
	}

	public GroupResponse create(GroupRequest group) throws SQLException {
		String sql = """
			INSERT INTO groups (id, name, specialty, main_subject, academic_year)
			VALUES (?, ?, ?, ?, ?)
			""";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			bindGroup(statement, group);
			statement.executeUpdate();
			return findById(connection, group.id())
				.orElseThrow(() -> new SQLException("Le groupe créé est introuvable."));
		}
	}

	public Optional<GroupResponse> update(String id, GroupRequest group) throws SQLException {
		String sql = """
			UPDATE groups
			SET name = ?, specialty = ?, main_subject = ?, academic_year = ?
			WHERE id = ?
			""";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, group.name());
			statement.setString(2, group.specialty());
			statement.setString(3, group.mainSubject());
			statement.setString(4, group.academicYear());
			statement.setString(5, id);
			if (statement.executeUpdate() == 0) {
				return Optional.empty();
			}
			return findById(connection, id);
		}
	}

	public DeleteResult deleteIfUnused(String id) throws SQLException {
		String sql = """
			DELETE FROM groups
			WHERE id = ?
			  AND NOT EXISTS (SELECT 1 FROM students WHERE group_id = ?)
			  AND NOT EXISTS (SELECT 1 FROM teacher_groups WHERE group_id = ?)
			""";
		try (Connection connection = databaseConfig.openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, id);
			statement.setString(2, id);
			statement.setString(3, id);
			if (statement.executeUpdate() > 0) {
				return DeleteResult.DELETED;
			}
			return findById(connection, id).isEmpty()
				? DeleteResult.NOT_FOUND
				: DeleteResult.IN_USE;
		}
	}

	private Optional<GroupResponse> findById(Connection connection, String id) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS + " WHERE id = ?")) {
			statement.setString(1, id);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next()
					? Optional.of(mapGroup(connection, resultSet))
					: Optional.empty();
			}
		}
	}

	private GroupResponse mapGroup(Connection connection, ResultSet resultSet) throws SQLException {
		String id = resultSet.getString("id");
		return new GroupResponse(
			id,
			resultSet.getString("name"),
			resultSet.getString("specialty"),
			resultSet.getString("main_subject"),
			resultSet.getString("academic_year"),
			countStudents(connection, id),
			findTeacherIds(connection, id),
			resultSet.getString("created_at")
		);
	}

	private int countStudents(Connection connection, String groupId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT COUNT(*) FROM students WHERE group_id = ?")) {
			statement.setString(1, groupId);
			try (ResultSet resultSet = statement.executeQuery()) {
				resultSet.next();
				return resultSet.getInt(1);
			}
		}
	}

	private List<Long> findTeacherIds(Connection connection, String groupId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT teacher_id FROM teacher_groups WHERE group_id = ? ORDER BY teacher_id")) {
			statement.setString(1, groupId);
			try (ResultSet resultSet = statement.executeQuery()) {
				List<Long> teacherIds = new ArrayList<>();
				while (resultSet.next()) {
					teacherIds.add(resultSet.getLong("teacher_id"));
				}
				return teacherIds;
			}
		}
	}

	private void bindGroup(PreparedStatement statement, GroupRequest group) throws SQLException {
		statement.setString(1, group.id());
		statement.setString(2, group.name());
		statement.setString(3, group.specialty());
		statement.setString(4, group.mainSubject());
		statement.setString(5, group.academicYear());
	}

	public enum DeleteResult {
		DELETED,
		NOT_FOUND,
		IN_USE
	}
}
