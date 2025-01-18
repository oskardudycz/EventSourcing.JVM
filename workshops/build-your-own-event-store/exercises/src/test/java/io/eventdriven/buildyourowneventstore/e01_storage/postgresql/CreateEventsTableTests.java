package io.eventdriven.buildyourowneventstore.e01_storage.postgresql;

import io.eventdriven.buildyourowneventstore.EventStore;
import io.eventdriven.buildyourowneventstore.PgEventStore;
import io.eventdriven.buildyourowneventstore.tools.PostgresTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.eventdriven.buildyourowneventstore.tools.PostgresSchemaProvider.Column.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
  @Tag("Exercise")
  @Test
  public void eventsTable_ShouldBeCreated() {
    var eventsTable = schemaProvider.getTable(EventsTableName);

    assertTrue(eventsTable.isPresent());
    assertEquals(EventsTableName, eventsTable.get().getName());
  }

  /**
   * Verifies if Stream table has Stream id column of type UUID
   */
  @Tag("Exercise")
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
   * Verifies if Stream table has StreamPosition column of type bigint
   */
  @Tag("Exercise")
  @Test
  public void eventsTable_ShouldHave_StreamPositionColumn_WithLongType() {
    var streamPositionColumn = schemaProvider
      .getTable(EventsTableName)
      .flatMap(table -> table.getColumn(streamPositionColumnName));

    assertTrue(streamPositionColumn.isPresent());
    assertEquals(streamPositionColumnName, streamPositionColumn.get().getName());
    assertEquals(bigintType, streamPositionColumn.get().getType());
  }

  /**
   * Verifies if Stream table has GlobalPosition column of type bigint
   */
  @Tag("Exercise")
  @Test
  public void eventsTable_ShouldHave_GlobalPositionColumn_WithLongType() {
    var globalPositionColumn = schemaProvider
      .getTable(EventsTableName)
      .flatMap(table -> table.getColumn(globalPositionColumnName));

    assertTrue(globalPositionColumn.isPresent());
    assertEquals(globalPositionColumnName, globalPositionColumn.get().getName());
    assertEquals(bigintType, globalPositionColumn.get().getType());
  }

  /**
   * Verifies if Stream table has id column of type UUID
   */
  @Tag("Exercise")
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
   * Verifies if Stream table has data column of type JSONB
   */
  @Tag("Exercise")
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
   * Verifies if Stream table has data column of type JSONB
   */
  @Tag("Exercise")
  @Test
  public void eventsTable_ShouldHave_MetadataColumn() {
    var dataColumn = schemaProvider
      .getTable(EventsTableName)
      .flatMap(table -> table.getColumn(metaDataColumnName));

    assertTrue(dataColumn.isPresent());
    assertEquals(metaDataColumnName, dataColumn.get().getName());
    assertEquals(jsonbType, dataColumn.get().getType());
  }

  /**
   * Verifies if Stream table has Type column of type String
   */
  @Tag("Exercise")
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
   * Verifies if Stream table has Version column of type Timestamp with time zone
   */
  @Tag("Exercise")
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
