package io.eventdriven.ecommerce;

import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.eventstore.RecordedEventsConfiguration;
import io.eventdriven.testing.tracing.InMemorySpansConfiguration;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DirtiesContext
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "messaging.bus=kafka",
            "eventstore.type=kurrentdb",
            "projections.database=postgresql",
            "messaging.retry.initial-interval=100ms"
        })
@AutoConfigureTracing
@Import({OutsideWorld.class, InMemorySpansConfiguration.class, RecordedEventsConfiguration.class})
public class PurchaseOnKafkaTests extends PurchaseTests {
    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        var postgreSQL = TestDatabases.newPostgreSQLDatabase();

        registry.add("spring.kafka.bootstrap-servers", TestDatabases::kafkaBootstrapServers);
        registry.add("kurrentdb.connectionstring", TestDatabases::kurrentDBConnectionString);
        TestDatabases.addProjectionsDatabase(registry, postgreSQL);
    }
}
