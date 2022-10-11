package io.eventdriven.buildyourowneventstore.e01_create_streams_table;

import io.eventdriven.buildyourowneventstore.tools.PostgresTest;
import org.junit.jupiter.api.Test;

import static io.eventdriven.buildyourowneventstore.tools.PostgresSchemaProvider.Column.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateStreamsTableTests extends PostgresTest {
    private final String streamsTableName = "streams";

    private final String idColumnName = "id";
    private final String typeColumnName = "type";
    private final String versionColumnName = "version";

    /**
     * Verifies if Stream table was created
     */
    @Test
    public void streamsTable_ShouldBeCreated() {
        var streamsTable = schemaProvider.getTable(streamsTableName);

        assertTrue(streamsTable.isPresent());
        assertEquals(streamsTableName, streamsTable.get().getName());
    }

    /**
     * Verifies if Stream table has id column of type UUID
     */
    @Test
    public void streamsTable_ShouldHave_IdColumn() {
        var idColumn = schemaProvider
            .getTable(streamsTableName)
            .flatMap(table -> table.getColumn(idColumnName));

        assertTrue(idColumn.isPresent());
        assertEquals(idColumnName, idColumn.get().getName());
        assertEquals(uuidType, idColumn.get().getType());
    }

    /**
     * Verifies if Stream table has Type column of type String
     */
    @Test
    public void streamsTable_ShouldHave_TypeColumn_WithStringType() {
        var typeColumn = schemaProvider
            .getTable(streamsTableName)
            .flatMap(table -> table.getColumn(typeColumnName));

        assertTrue(typeColumn.isPresent());
        assertEquals(typeColumnName, typeColumn.get().getName());
        assertEquals(textType, typeColumn.get().getType());
    }

    /**
     * Verifies if Stream table has Version column of type Long
     */
    @Test
    public void streamsTable_ShouldHave_VersionColumn_WithLongType() {
        var versionColumn = schemaProvider
            .getTable(streamsTableName)
            .flatMap(table -> table.getColumn(versionColumnName));

        assertTrue(versionColumn.isPresent());
        assertEquals(versionColumnName, versionColumn.get().getName());
        assertEquals(bigintType, versionColumn.get().getType());
    }
}
