package io.eventdriven.buildyourowneventstore.e01_storage.postgresql;

import static io.eventdriven.buildyourowneventstore.tools.PostgresSchemaProvider.Column.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.buildyourowneventstore.tools.PostgresTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class CreateStreamsTableTests extends PostgresTest {
    private final String streamsTableName = "streams";

    private final String idColumnName = "id";
    private final String typeColumnName = "type";
    private final String streamPositionColumnName = "stream_position";

    @BeforeAll
    public void setup() {
        // Create Event Store
        var eventStore = new PostgreSQLEventStore(dbConnection);

        // Initialize Event Store
        eventStore.init();
    }

    /**
     * Verifies if Stream table was created
     */
    @Tag("Exercise")
    @Test
    public void streamsTable_ShouldBeCreated() {
        var streamsTable = schemaProvider.getTable(streamsTableName);

        assertThat(streamsTable.isPresent()).isTrue();
        assertThat(streamsTable.get().getName()).isEqualTo(streamsTableName);
    }

    /**
     * Verifies if Stream table has id column of type UUID
     */
    @Tag("Exercise")
    @Test
    public void streamsTable_ShouldHave_IdColumn() {
        var idColumn = schemaProvider.getTable(streamsTableName).flatMap(table -> table.getColumn(idColumnName));

        assertThat(idColumn.isPresent()).isTrue();
        assertThat(idColumn.get().getName()).isEqualTo(idColumnName);
        assertThat(idColumn.get().getType()).isEqualTo(uuidType);
    }

    /**
     * Verifies if Stream table has Type column of type String
     */
    @Tag("Exercise")
    @Test
    public void streamsTable_ShouldHave_TypeColumn_WithStringType() {
        var typeColumn = schemaProvider.getTable(streamsTableName).flatMap(table -> table.getColumn(typeColumnName));

        assertThat(typeColumn.isPresent()).isTrue();
        assertThat(typeColumn.get().getName()).isEqualTo(typeColumnName);
        assertThat(typeColumn.get().getType()).isEqualTo(textType);
    }

    /**
     * Verifies if Stream table has StreamPosition column of type Long
     */
    @Tag("Exercise")
    @Test
    public void streamsTable_ShouldHave_StreamPositionColumn_WithLongType() {
        var streamPositionColumn =
                schemaProvider.getTable(streamsTableName).flatMap(table -> table.getColumn(streamPositionColumnName));

        assertThat(streamPositionColumn.isPresent()).isTrue();
        assertThat(streamPositionColumn.get().getName()).isEqualTo(streamPositionColumnName);
        assertThat(streamPositionColumn.get().getType()).isEqualTo(bigintType);
    }
}
