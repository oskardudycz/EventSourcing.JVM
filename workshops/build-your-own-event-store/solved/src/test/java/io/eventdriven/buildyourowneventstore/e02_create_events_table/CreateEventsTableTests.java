package io.eventdriven.buildyourowneventstore.e02_create_events_table;

import io.eventdriven.buildyourowneventstore.tools.PostgresTest;
import org.junit.jupiter.api.Test;

import static io.eventdriven.buildyourowneventstore.tools.PostgresSchemaProvider.Column.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateEventsTableTests extends PostgresTest {
    private final String EventsTableName = "events";

    private final String idColumnName = "id";
    private final String streamIdColumnName = "stream_id";
    private final String dataColumnName = "data";
    private final String typeColumnName = "type";
    private final String versionColumnName = "version";
    private final String createdColumnName = "created";

    /**
     * Verifies if Stream table was created
     */
    @Test
    public void eventsTable_ShouldBeCreated() {
        var eventsTable = schemaProvider.getTable(EventsTableName);

        assertTrue(eventsTable.isPresent());
        assertEquals(EventsTableName, eventsTable.get().getName());
    }

    /**
     * Verifies if Stream table has id column of type UUID
     */
    @Test
    public void eventsTable_ShouldHave_IdColumn() {
        var idColumn = schemaProvider
            .getTable(EventsTableName)
            .flatMap(table -> table.getColumn(idColumnName));

        assertTrue(idColumn.isPresent());
        assertEquals(idColumnName, idColumn.get().getName());
        assertEquals(uuidType, idColumn.get().getType());
    }

    /**
     * Verifies if Stream table has Stream id column of type UUID
     */
    @Test
    public void eventsTable_ShouldHave_StreamIdColumn() {
        var streamIdColumn = schemaProvider
            .getTable(EventsTableName)
            .flatMap(table -> table.getColumn(streamIdColumnName));

        assertTrue(streamIdColumn.isPresent());
        assertEquals(streamIdColumnName, streamIdColumn.get().getName());
        assertEquals(uuidType, streamIdColumn.get().getType());
    }

    /**
     * Verifies if Stream table has data column of type JSONB
     */
    @Test
    public void eventsTable_ShouldHave_DataColumn() {
        var dataColumn = schemaProvider
            .getTable(EventsTableName)
            .flatMap(table -> table.getColumn(dataColumnName));

        assertTrue(dataColumn.isPresent());
        assertEquals(dataColumnName, dataColumn.get().getName());
        assertEquals(jsonbType, dataColumn.get().getType());
    }

    /**
     * Verifies if Stream table has Type column of type String
     */
    @Test
    public void eventsTable_ShouldHave_TypeColumn_WithStringType() {
        var typeColumn = schemaProvider
            .getTable(EventsTableName)
            .flatMap(table -> table.getColumn(typeColumnName));

        assertTrue(typeColumn.isPresent());
        assertEquals(typeColumnName, typeColumn.get().getName());
        assertEquals(textType, typeColumn.get().getType());
    }

    /**
     * Verifies if Stream table has Version column of type bigint
     */
    @Test
    public void eventsTable_ShouldHave_VersionColumn_WithLongType() {
        var versionColumn = schemaProvider
            .getTable(EventsTableName)
            .flatMap(table -> table.getColumn(versionColumnName));

        assertTrue(versionColumn.isPresent());
        assertEquals(versionColumnName, versionColumn.get().getName());
        assertEquals(bigintType, versionColumn.get().getType());
    }

    /**
     * Verifies if Stream table has Version column of type Timestamp with time zone
     */
    @Test
    public void eventsTable_ShouldHave_CreatedColumn_WithLongType() {
        var createdColumn = schemaProvider
            .getTable(EventsTableName)
            .flatMap(table -> table.getColumn(createdColumnName));

        assertTrue(createdColumn.isPresent());
        assertEquals(createdColumnName, createdColumn.get().getName());
        assertEquals(timestampWithTimeZone, createdColumn.get().getType());
    }
}
