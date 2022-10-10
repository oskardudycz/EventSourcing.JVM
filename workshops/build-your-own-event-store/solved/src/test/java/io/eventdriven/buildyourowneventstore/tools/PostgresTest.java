package io.eventdriven.buildyourowneventstore.tools;

import io.eventdriven.buildyourowneventstore.EventStore;
import io.eventdriven.buildyourowneventstore.PgEventStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.sql.Connection;
import java.sql.SQLException;

public class PostgresTest {
    protected static Connection dbConnection;
    protected static PostgresSchemaProvider schemaProvider;
    protected static EventStore eventStore;

    @BeforeAll
    public static void setup() {
        dbConnection = PostgresDbConnectionProvider.getFreshDbConnection();
        schemaProvider = new PostgresSchemaProvider(dbConnection);

        // Create Event Store
        eventStore = new PgEventStore(dbConnection);

        // Initialize Event Store
        eventStore.Init();
    }

    @AfterAll
    public static void tearDown() throws SQLException {
        dbConnection.close();
    }
}
