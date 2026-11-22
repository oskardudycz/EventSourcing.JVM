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
        properties = {"messaging.bus=rabbitmq", "eventstore.type=postgresql", "projections.database=postgresql"})
@AutoConfigureTracing
@Import({OutsideWorld.class, InMemorySpansConfiguration.class, RecordedEventsConfiguration.class})
public class PurchaseOnRabbitMQTests extends PurchaseTests {
    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        var postgreSQL = TestDatabases.newPostgreSQLDatabase();
        var rabbitMQ = TestDatabases.rabbitMQ();

        TestDatabases.addProjectionsDatabase(registry, postgreSQL);
        TestDatabases.addPostgreSQLEventStore(registry, postgreSQL);
        TestDatabases.addRabbitMQ(registry, rabbitMQ);
    }
}
