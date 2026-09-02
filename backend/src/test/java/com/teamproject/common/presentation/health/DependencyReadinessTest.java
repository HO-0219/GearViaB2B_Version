package com.teamproject.common.presentation.health;

import com.teamproject.resource.storage.DynamicFileStorage;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DependencyReadinessTest {
    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final DynamicFileStorage storage = mock(DynamicFileStorage.class);
    private final DependencyReadiness readiness = new DependencyReadiness(dataSource, storage);

    @Test
    void inactiveNasDoesNotFailLocalReadiness() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        when(storage.status()).thenReturn(new DynamicFileStorage.Status("local", List.of("local", "nas_mount"),
                "/data/uploads", true, "/data/nas", false));
        when(storage.activeHealth()).thenReturn(new DynamicFileStorage.ActiveHealth("local", true));

        assertThat(readiness.check().up()).isTrue();
    }

    @Test
    void databaseFailureIsReportedInternallyWithoutThrowing() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("mysql.internal password rejected"));
        when(storage.activeHealth()).thenReturn(new DynamicFileStorage.ActiveHealth("local", true));

        DependencyReadiness.ReadinessSnapshot snapshot = readiness.check();

        assertThat(snapshot.up()).isFalse();
        assertThat(snapshot.components()).extracting(DependencyReadiness.Component::name)
                .contains("database");
        assertThat(snapshot.components()).extracting(DependencyReadiness.Component::detail)
                .doesNotContain("mysql.internal password rejected");
    }
}
