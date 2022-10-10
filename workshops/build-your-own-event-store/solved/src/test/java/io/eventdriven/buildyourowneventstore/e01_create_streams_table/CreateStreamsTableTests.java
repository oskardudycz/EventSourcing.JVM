package io.eventdriven.buildyourowneventstore.e01_create_streams_table;

import io.eventdriven.buildyourowneventstore.PgEventStore;
import io.eventdriven.buildyourowneventstore.tools.PostgresDbConnectionProvider;
import io.eventdriven.buildyourowneventstore.tools.PostgresSchemaProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static io.eventdriven.buildyourowneventstore.tools.PostgresSchemaProvider.Column.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateStreamsTableTests {
    private static Connection dbConnection;
    private static PostgresSchemaProvider schemaProvider;

    private final String streamsTableName = "streams";

    private final String idColumnName = "id";
    private final String typeColumnName = "type";
    private final String versionColumnName = "version";

    @BeforeAll
    public static void setup() {
        dbConnection = PostgresDbConnectionProvider.getFreshDbConnection();
        schemaProvider = new PostgresSchemaProvider(dbConnection);

        // Create Event Store
        var eventStore = new PgEventStore(dbConnection);

        // Initialize Event Store
        eventStore.Init();
    }

    @AfterAll
    public static void tearDown() throws SQLException {
        dbConnection.close();
    }

    /**
     * Verifies if Stream table was created
     */
    @Test
    public void StreamsTable_ShouldBeCreated() {
        var streamsTable = schemaProvider.getTable(streamsTableName);

        assertTrue(streamsTable.isPresent());
        assertEquals(streamsTableName, streamsTable.get().getName());
    }

    /**
     * Verifies if Stream table has Id column of type Guid
     */
    @Test
    public void StreamsTable_ShouldHave_IdColumn() {
        var idColumn = schemaProvider
            .getTable(streamsTableName)
            .flatMap(table -> table.getColumn(idColumnName));

        assertTrue(idColumn.isPresent());
        assertEquals(idColumnName, idColumn.get().getName());
        assertEquals(guidType, idColumn.get().getType());
    }

    /**
     * Verifies if Stream table has Type column of type String
     */
    @Test
    public void StreamsTable_ShouldHave_TypeColumn_WithStringType() {
        var typeColumn = schemaProvider
            .getTable(streamsTableName)
            .flatMap(table -> table.getColumn(typeColumnName));

        assertTrue(typeColumn.isPresent());
        assertEquals(typeColumnName, typeColumn.get().getName());
        assertEquals(stringType, typeColumn.get().getType());
    }

    /**
     * Verifies if Stream table has Version column of type Long
     */
    @Test
    public void StreamsTable_ShouldHave_VersionColumn_WithLongType() {
        var versionColumn = schemaProvider
            .getTable(streamsTableName)
            .flatMap(table -> table.getColumn(versionColumnName));

        assertTrue(versionColumn.isPresent());
        assertEquals(versionColumnName, versionColumn.get().getName());
        assertEquals(longType, versionColumn.get().getType());
    }
}
