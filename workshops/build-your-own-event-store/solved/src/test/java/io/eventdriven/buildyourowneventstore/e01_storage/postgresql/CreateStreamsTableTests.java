package io.eventdriven.buildyourowneventstore.e01_storage.postgresql;

import io.eventdriven.buildyourowneventstore.tools.PostgresTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.eventdriven.buildyourowneventstore.tools.PostgresSchemaProvider.Column.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
   * Verifies if Stream table has StreamPosition column of type Long
   */
  @Test
  public void streamsTable_ShouldHave_StreamPositionColumn_WithLongType() {
    var streamPositionColumn = schemaProvider
      .getTable(streamsTableName)
      .flatMap(table -> table.getColumn(streamPositionColumnName));

    assertTrue(streamPositionColumn.isPresent());
    assertEquals(streamPositionColumnName, streamPositionColumn.get().getName());
    assertEquals(bigintType, streamPositionColumn.get().getType());
  }
}
