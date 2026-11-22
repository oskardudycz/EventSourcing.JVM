package io.eventdriven.core.eventstore.postgresql;

import com.zaxxer.hikari.HikariDataSource;
import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.EventStoreMetadataTests;
import io.eventdriven.core.processing.RetryBackOff;
import io.eventdriven.core.subscriptions.SubscriptionCheckpoints;
import io.eventdriven.testing.projections.ProjectionsDatabase;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;

public class PostgreSQLEventStoreMetadataTests extends EventStoreMetadataTests {
    private static final HikariDataSource dataSource = PostgreSQLTestDatabase.dataSource();

    private final PostgreSQLEventStore eventStore = new PostgreSQLEventStore(
            dataSource,
            context,
            serializer(),
            ProjectionsDatabase.H2.bean(SubscriptionCheckpoints.class),
            RetryBackOff.exponential(Duration.ofMillis(100)),
            Duration.ofMillis(50));

    @Override
    protected EventStore eventStore() {
        return eventStore;
    }

    @AfterEach
    void closeStore() {
        eventStore.close();
    }

    @AfterAll
    static void closeDataSource() {
        dataSource.close();
    }
}
