package com.teamproject.migration;

import com.teamproject.B2BGearViaApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class MySqlFlywayMigrationTest {
    @Container
    // Test-only schema: keep migration verification isolated from the live b2bgearvia database.
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("b2bgearvia_migration")
                    .withUsername("b2bgearvia")
                    .withPassword("b2bgearvia");

    @Test
    void migratesFreshMySqlSchemaAndIsIdempotent() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();
        long adminsBeforeRerun = countRows("users", "role", "ADMIN");
        long tablesAfterFirstMigration = countSchemaObjects("information_schema.tables", "table_name", List.of("users", "work_groups", "tasks", "push_subscriptions"));
        flyway.migrate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(countSchemaObjects("information_schema.tables", "table_name", List.of("users", "work_groups", "tasks", "push_subscriptions"))).isEqualTo(tablesAfterFirstMigration);
        assertThat(countRows("users", "role", "ADMIN")).isEqualTo(adminsBeforeRerun);
        assertThat(countSchemaObjects("information_schema.tables", "table_name", List.of("payment_methods", "payment_attempts", "group_subscriptions", "subscription_consents", "oauth_signup_requests", "social_accounts"))).isZero();
        assertThat(countColumns("work_groups", List.of("membership_plan", "paid_period_start", "paid_period_end"))).isZero();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(countSchemaObjects(
                "information_schema.tables",
                "table_name",
                List.of(
                        "reports",
                        "ai_weekly_report_revision",
                        "task_activity_events",
                        "weekly_objectives",
                        "task_weekly_objective_links",
                        "ai_assistant_actions",
                        "ai_assistant_messages",
                        "projects",
                        "project_issue_nodes",
                        "project_issue_checklist_items",
                        "project_issue_images",
                        "project_documents",
                        "chat_channels",
                        "chat_messages",
                        "chat_socket_tickets",
                        "task_assignee_change_requests",
                        "emergency_issues")))
                .isEqualTo(17);
        assertThat(countSchemaObjects(
                "information_schema.tables",
                "table_name",
                List.of("scheduled_job_locks")))
                .isEqualTo(1);
        assertThat(countColumns(
                "report_deliveries",
                List.of("retry_count", "last_attempt_at", "next_retry_at")))
                .isEqualTo(3);
        assertThat(countColumns(
                "reports",
                List.of(
                        "ai_context_json",
                        "reference_index_json",
                        "evidence_json",
                        "editorial_json",
                        "publication_status",
                        "editor_version")))
                .isEqualTo(6);
        assertThat(countColumns(
                "tasks",
                List.of(
                        "blocker_type",
                        "blocker_next_action_type",
                        "blocker_review_date",
                        "project_id",
                        "project_topic_id",
                        "deleted_at")))
                .isEqualTo(6);
        assertThat(countColumns(
                "projects",
                List.of(
                        "group_id",
                        "lead_member_id",
                        "created_by_member_id",
                        "status",
                        "start_date",
                        "due_date",
                        "version")))
                .isEqualTo(7);
        assertThat(countColumns(
                "project_issue_nodes",
                List.of("project_id", "parent_id", "assignee_member_id", "level", "status", "archived_at", "version")))
                .isEqualTo(7);
        assertThat(countColumns(
                "project_documents",
                List.of("project_id", "issue_node_id", "document_type", "storage_key", "size_bytes", "deleted_at")))
                .isEqualTo(6);
        assertThat(countColumns(
                "chat_messages",
                List.of("channel_id", "sender_member_id", "message_type", "storage_key", "size_bytes", "created_at")))
                .isEqualTo(6);

        // Flyway SQL이 성공하는 것만으로는 운영의 Hibernate validate 타입 불일치를 잡지 못한다.
        // 실제 운영과 같은 MySQL 스키마 위에서 애플리케이션 컨텍스트까지 시작해 매핑을 검증한다.
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(B2BGearViaApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + MYSQL.getJdbcUrl(),
                        "--spring.datasource.username=" + MYSQL.getUsername(),
                        "--spring.datasource.password=" + MYSQL.getPassword(),
                        "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                        "--spring.flyway.enabled=false",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--app.environment=test",
                        "--app.demo.enabled=false",
                        "--app.mail.enabled=false",
                        "--app.ai-report.enabled=false",
                        "--app.ai-assistant.enabled=false")) {
            assertThat(ignored.isActive()).isTrue();
        }
    }

    private long countColumns(String table, List<String> columns) throws Exception {
        String placeholders = String.join(",", columns.stream().map(ignored -> "?").toList());
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name IN (%s)
                """.formatted(placeholders);
        try (Connection connection = MYSQL.createConnection("");
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            for (int index = 0; index < columns.size(); index++) {
                statement.setString(index + 2, columns.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private long countRows(String table, String column, String value) throws Exception {
        try (Connection connection = MYSQL.createConnection("");
                PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?")) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }

    private long countSchemaObjects(
            String informationSchemaTable, String nameColumn, List<String> names)
            throws Exception {
        String placeholders = String.join(",", names.stream().map(ignored -> "?").toList());
        String sql = """
                SELECT COUNT(*)
                FROM %s
                WHERE table_schema = DATABASE()
                  AND %s IN (%s)
                """.formatted(informationSchemaTable, nameColumn, placeholders);
        try (Connection connection = MYSQL.createConnection("");
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < names.size(); index++) {
                statement.setString(index + 1, names.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }
}
