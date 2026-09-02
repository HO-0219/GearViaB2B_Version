package com.teamproject.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class MySqlOperationalIndexTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("b2bgearvia_index_plan")
            .withUsername("b2bgearvia")
            .withPassword("b2bgearvia");

    private static long groupId;
    private static long memberId;
    private static long projectId;

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        try (Connection connection = MYSQL.createConnection("")) {
            connection.setAutoCommit(false);
            long userId = insertId(connection, """
                    INSERT INTO users (username, email, password_hash, name, email_verified, created_at)
                    VALUES ('plan_user', 'plan_user@example.com', 'hash', 'Plan User', TRUE, NOW(6))
                    """);
            groupId = insertId(connection, """
                    INSERT INTO work_groups (type, name, timezone, dashboard_visibility, created_by, created_at, updated_at)
                    VALUES ('TEAM', 'Plan Group', 'Asia/Seoul', 'MEMBERS', %d, NOW(6), NOW(6))
                    """.formatted(userId));
            memberId = insertId(connection, """
                    INSERT INTO group_members (group_id, user_id, role, status, joined_at)
                    VALUES (%d, %d, 'LEADER', 'ACTIVE', NOW(6))
                    """.formatted(groupId, userId));
            projectId = insertId(connection, """
                    INSERT INTO projects (group_id, lead_member_id, created_by_member_id, name, status,
                        created_at, updated_at, version)
                    VALUES (%d, %d, %d, 'Index Plan', 'ACTIVE', NOW(6), NOW(6), 0)
                    """.formatted(groupId, memberId, memberId));
            seedTasks(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute("ANALYZE TABLE tasks");
            }
            connection.commit();
        }
    }

    @Test
    void optimizerUsesExistingOperationalIndexes() throws Exception {
        assertThat(explainKey("""
                SELECT id FROM tasks
                WHERE group_id = ? AND status = 'TODO' AND due_at IS NOT NULL
                ORDER BY due_at LIMIT 50
                """, groupId)).isEqualTo("idx_tasks_group_status_due");
        assertThat(explainKey("""
                SELECT id FROM tasks
                WHERE assignee_member_id = ? AND status = 'TODO' AND due_at IS NOT NULL
                ORDER BY due_at LIMIT 50
                """, memberId)).isEqualTo("idx_tasks_assignee_status_due");
        assertThat(explainKey("""
                SELECT id FROM tasks
                WHERE project_id = ? AND status = 'TODO' AND deleted_at IS NULL
                LIMIT 50
                """, projectId)).isEqualTo("idx_tasks_project_status");
        assertThat(explainKey("""
                SELECT id FROM tasks
                WHERE group_id = ?
                ORDER BY created_at DESC, id DESC LIMIT 50
                """, groupId)).isEqualTo("idx_tasks_group_created");
    }

    private static void seedTasks(Connection connection) throws Exception {
        String sql = """
                INSERT INTO tasks (group_id, project_id, requester_member_id, assignee_member_id,
                    title, description, priority, status, due_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, 0)
                """;
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < 10_000; index++) {
                LocalDateTime timestamp = base.plusSeconds(index);
                statement.setLong(1, groupId);
                statement.setLong(2, projectId);
                statement.setLong(3, memberId);
                statement.setLong(4, memberId);
                statement.setString(5, "task-" + index);
                statement.setString(6, index % 4 == 0 ? "HIGH" : "NORMAL");
                statement.setString(7, index % 2 == 0 ? "TODO" : "IN_PROGRESS");
                statement.setTimestamp(8, Timestamp.valueOf(timestamp.plusDays(7)));
                statement.setTimestamp(9, Timestamp.valueOf(timestamp));
                statement.setTimestamp(10, Timestamp.valueOf(timestamp));
                statement.addBatch();
                if ((index + 1) % 500 == 0) {
                    statement.executeBatch();
                }
            }
        }
    }

    private static long insertId(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    private String explainKey(String query, Object... parameters) throws Exception {
        try (Connection connection = MYSQL.createConnection("");
                PreparedStatement statement = connection.prepareStatement("EXPLAIN " + query)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString("key");
            }
        }
    }
}
