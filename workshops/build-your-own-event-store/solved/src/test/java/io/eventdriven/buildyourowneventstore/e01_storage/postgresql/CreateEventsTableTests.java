package io.eventdriven.buildyourowneventstore.e01_storage.postgresql;

import static io.eventdriven.buildyourowneventstore.tools.PostgresSchemaProvider.Column.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.buildyourowneventstore.tools.PostgresTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CreateEventsTableTests extends PostgresTest {
    private final String EventsTableName = "events";

    private final String streamIdColumnName = "stream_id";
    private final String streamPositionColumnName = "stream_position";
    private final String globalPositionColumnName = "global_position";
    private final String idColumnName = "id";
    private final String dataColumnName = "data";
    private final String metaDataColumnName = "metadata";
    private final String typeColumnName = "type";
    private final String createdColumnName = "created";

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
    @Test
    public void eventsTable_ShouldBeCreated() {
        var eventsTable = schemaProvider.getTable(EventsTableName);

        assertThat(eventsTable.isPresent()).isTrue();
        assertThat(eventsTable.get().getName()).isEqualTo(EventsTableName);
    }

    /**
     * Verifies if Stream table has Stream id column of type TEXT
     */
    @Test
    public void eventsTable_ShouldHave_StreamIdColumn() {
        var streamIdColumn =
                schemaProvider.getTable(EventsTableName).flatMap(table -> table.getColumn(streamIdColumnName));

        assertThat(streamIdColumn.isPresent()).isTrue();
        assertThat(streamIdColumn.get().getName()).isEqualTo(streamIdColumnName);
        assertThat(streamIdColumn.get().getType()).isEqualTo(textType);
    }

    /**
     * Verifies if Stream table has StreamPosition column of type bigint
     */
    @Test
    public void eventsTable_ShouldHave_StreamPositionColumn_WithLongType() {
        var streamPositionColumn =
                schemaProvider.getTable(EventsTableName).flatMap(table -> table.getColumn(streamPositionColumnName));

        assertThat(streamPositionColumn.isPresent()).isTrue();
        assertThat(streamPositionColumn.get().getName()).isEqualTo(streamPositionColumnName);
        assertThat(streamPositionColumn.get().getType()).isEqualTo(bigintType);
    }

    /**
     * Verifies if Stream table has GlobalPosition column of type bigint
     */
    @Test
    public void eventsTable_ShouldHave_GlobalPositionColumn_WithLongType() {
        var globalPositionColumn =
                schemaProvider.getTable(EventsTableName).flatMap(table -> table.getColumn(globalPositionColumnName));

        assertThat(globalPositionColumn.isPresent()).isTrue();
        assertThat(globalPositionColumn.get().getName()).isEqualTo(globalPositionColumnName);
        assertThat(globalPositionColumn.get().getType()).isEqualTo(bigintType);
    }

    /**
     * Verifies if Stream table has id column of type TEXT
     */
    @Test
    public void eventsTable_ShouldHave_IdColumn() {
        var idColumn = schemaProvider.getTable(EventsTableName).flatMap(table -> table.getColumn(idColumnName));

        assertThat(idColumn.isPresent()).isTrue();
        assertThat(idColumn.get().getName()).isEqualTo(idColumnName);
        assertThat(idColumn.get().getType()).isEqualTo(textType);
    }

    /**
     * Verifies if Stream table has data column of type JSONB
     */
    @Test
    public void eventsTable_ShouldHave_DataColumn() {
        var dataColumn = schemaProvider.getTable(EventsTableName).flatMap(table -> table.getColumn(dataColumnName));

        assertThat(dataColumn.isPresent()).isTrue();
        assertThat(dataColumn.get().getName()).isEqualTo(dataColumnName);
        assertThat(dataColumn.get().getType()).isEqualTo(jsonbType);
    }

    /**
     * Verifies if Stream table has data column of type JSONB
     */
    @Test
    public void eventsTable_ShouldHave_MetadataColumn() {
        var dataColumn = schemaProvider.getTable(EventsTableName).flatMap(table -> table.getColumn(metaDataColumnName));

        assertThat(dataColumn.isPresent()).isTrue();
        assertThat(dataColumn.get().getName()).isEqualTo(metaDataColumnName);
        assertThat(dataColumn.get().getType()).isEqualTo(jsonbType);
    }

    /**
     * Verifies if Stream table has Type column of type String
     */
    @Test
    public void eventsTable_ShouldHave_TypeColumn_WithStringType() {
        var typeColumn = schemaProvider.getTable(EventsTableName).flatMap(table -> table.getColumn(typeColumnName));

        assertThat(typeColumn.isPresent()).isTrue();
        assertThat(typeColumn.get().getName()).isEqualTo(typeColumnName);
        assertThat(typeColumn.get().getType()).isEqualTo(textType);
    }

    /**
     * Verifies if Stream table has Version column of type Timestamp with time zone
     */
    @Test
    public void eventsTable_ShouldHave_CreatedColumn_WithLongType() {
        var createdColumn =
                schemaProvider.getTable(EventsTableName).flatMap(table -> table.getColumn(createdColumnName));

        assertThat(createdColumn.isPresent()).isTrue();
        assertThat(createdColumn.get().getName()).isEqualTo(createdColumnName);
        assertThat(createdColumn.get().getType()).isEqualTo(timestampWithTimeZone);
    }
}
