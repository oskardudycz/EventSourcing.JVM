package io.eventdriven.buildyourowneventstore.e01_storage.postgresql;

import io.eventdriven.buildyourowneventstore.e01_storage.EventStore;

import java.sql.Connection;

import static io.eventdriven.buildyourowneventstore.tools.SqlInvoker.executeSql;

public class PostgreSQLEventStore implements EventStore {
  private final Connection dbConnection;

  public PostgreSQLEventStore(Connection dbConnection) {
    this.dbConnection = dbConnection;
  }

  @Override
  public void init() {
    executeSql(dbConnection, createStreamsTableSql);
    executeSql(dbConnection, createEventsTableSql);
  }

  private final String createStreamsTableSql = """
    CREATE TABLE IF NOT EXISTS streams(
        id               UUID                      NOT NULL    PRIMARY KEY,
        type             TEXT                      NOT NULL,
        stream_position  BIGINT                    NOT NULL
    );
    """;

  private final String createEventsTableSql = """
    CREATE SEQUENCE IF NOT EXISTS global_event_position;

    CREATE TABLE IF NOT EXISTS events(
          stream_id        UUID                      NOT NULL,
          stream_position  BIGINT                    NOT NULL,
          global_position  BIGINT                    DEFAULT nextval('global_event_position'),
          id               UUID                      NOT NULL,
          data             JSONB                     NOT NULL,
          metadata         JSONB                     NOT NULL,
          type             TEXT                      NOT NULL,
          created          timestamp with time zone  NOT NULL    default (now()),
          FOREIGN KEY(stream_id) REFERENCES streams(id),
          PRIMARY KEY (stream_id, stream_position)
    );
    """;
}
