package io.eventdriven.distributedprocesses.core.kurrentdb;

import com.zaxxer.hikari.HikariDataSource;
import io.eventdriven.distributedprocesses.core.processing.RetryBackOff;
import io.eventdriven.distributedprocesses.core.projections.ProcessorCheckpoints;
import io.eventdriven.testing.ProjectionsDatabase;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;

public class PostgreSQLEventStoreMetadataTests extends EventStoreMetadataTests {
    private final HikariDataSource dataSource = PostgreSQLTestDatabase.dataSource();
    private final PostgreSQLEventStore eventStore = new PostgreSQLEventStore(
            dataSource,
            context,
            ProjectionsDatabase.H2.bean(ProcessorCheckpoints.class),
            RetryBackOff.exponential(Duration.ofMillis(100)),
            Duration.ofMillis(50));

    @Override
    protected EventStore eventStore() {
        return eventStore;
    }

    @AfterEach
    void closeStore() {
        eventStore.close();
        dataSource.close();
    }
}
