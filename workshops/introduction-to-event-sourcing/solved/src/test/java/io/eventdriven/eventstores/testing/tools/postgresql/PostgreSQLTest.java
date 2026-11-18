package io.eventdriven.eventstores.testing.tools.postgresql;

import io.eventdriven.eventstores.postgresql.PostgreSQLEventStore;
import io.eventdriven.eventstores.postgresql.tools.PostgresSchemaProvider;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PostgreSQLTest {
    protected static Connection dbConnection;
    protected static PostgresSchemaProvider schemaProvider;

    @BeforeAll
    public void setupConnection() {
        dbConnection = PostgreSQLDbConnectionProvider.getFreshDbConnection();
        schemaProvider = new PostgresSchemaProvider(dbConnection);
    }

    public static PostgreSQLEventStore getPostgreSQLEventStore() {
        // Create Event Store
        var eventStore = new PostgreSQLEventStore(dbConnection);

        // Initialize Event Store
        eventStore.init();

        return eventStore;
    }

    @AfterAll
    public void tearDown() throws SQLException {
        dbConnection.close();
    }
}
