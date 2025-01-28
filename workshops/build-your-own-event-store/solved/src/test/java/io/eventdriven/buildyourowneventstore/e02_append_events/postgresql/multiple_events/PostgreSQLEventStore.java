package io.eventdriven.buildyourowneventstore.e02_append_events.postgresql.multiple_events;

import io.eventdriven.buildyourowneventstore.EventTypeMapper;
import io.eventdriven.buildyourowneventstore.JsonEventSerializer;
import io.eventdriven.buildyourowneventstore.e02_append_events.EventStore;

import java.sql.Connection;
import java.util.Arrays;
import java.util.UUID;

import static io.eventdriven.buildyourowneventstore.JsonEventSerializer.serialize;
import static io.eventdriven.buildyourowneventstore.tools.SqlInvoker.*;

public class PostgreSQLEventStore implements EventStore {
  private final Connection dbConnection;

  public PostgreSQLEventStore(Connection dbConnection) {
    this.dbConnection = dbConnection;
  }

  @Override
  public void init() {
    executeSql(dbConnection, createStreamsTableSql);
    executeSql(dbConnection, createEventsTableSql);
    executeSql(dbConnection, createAppendFunctionSql);
  }

  @Override
  public <Stream> void appendEvents(
    Class<Stream> streamClass,
    String streamId,
    Long expectedStreamPosition,
    Object... events
  ) {

    runInTransaction(dbConnection, connection ->
    {
      var ids = Arrays.stream(events)
        .map(_ -> UUID.randomUUID().toString())
        .toArray(String[]::new);

      var eventData = Arrays.stream(events)
        .map(JsonEventSerializer::serialize)
        .toArray(String[]::new);

      var eventMetadata = Arrays.stream(events)
        .map(_ -> "{}")
        .toArray(String[]::new);

      var eventTypes = Arrays.stream(events)
        .map(event -> EventTypeMapper.toName(event.getClass()))
        .toArray(String[]::new);

      boolean succeeded = querySingleSql(
        connection,
        "SELECT append_event(?::uuid[], ?::jsonb[], ?::jsonb[], ?::text[], ?::uuid, ?, ?) AS succeeded",
        ps -> {
          setArrayOf(dbConnection, ps, 1, "uuid", ids);
          setArrayOf(dbConnection, ps, 2, "jsonb", eventData);
          setArrayOf(dbConnection, ps, 3, "jsonb", eventMetadata);
          setArrayOf(dbConnection, ps, 4, "text", eventTypes);
          setStringParam(ps, 5, streamId);
          setStringParam(ps, 6, streamClass.getTypeName());
          setLong(ps, 7, expectedStreamPosition);
        },
        rs -> getBoolean(rs, "succeeded")
      );

      if (!succeeded)
        throw new IllegalStateException("Expected stream position did not match the current stream position!");

    });
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
          metadata         JSONB                     DEFAULT '{}',
          type             TEXT                      NOT NULL,
          created          timestamp with time zone  NOT NULL    default (now()),
          FOREIGN KEY(stream_id) REFERENCES streams(id),
          PRIMARY KEY (stream_id, stream_position)
    );
    """;

  private final String createAppendFunctionSql = """
    CREATE OR REPLACE FUNCTION append_event(
        ids_array uuid[],
        data_array jsonb[],
        metadata_array jsonb[],
        types_array text[],
        stream_id uuid,
        stream_type text,
        expected_stream_position bigint default null
    ) RETURNS boolean
        LANGUAGE plpgsql
        AS $$
        DECLARE
            current_stream_position int;
        BEGIN
            -- get current stream stream position
            SELECT
                stream_position INTO current_stream_position
            FROM streams as s
            WHERE
                s.id = stream_id FOR UPDATE;

            -- if stream doesn't exist - create new one with stream_position 0
            IF current_stream_position IS NULL THEN
                current_stream_position := 0;

                INSERT INTO streams
                    (id, type, stream_position)
                VALUES
                    (stream_id, stream_type, current_stream_position);
            END IF;

            -- check optimistic concurrency
            IF expected_stream_position IS NOT NULL AND current_stream_position != expected_stream_position THEN
                RETURN FALSE;
            END IF;

            -- increment current stream position
            current_stream_position := current_stream_position + 1;

            -- append event
            INSERT INTO events
                (id, data, metadata, stream_id, type, stream_position)
            SELECT
                id,
                data,
                metadata,
                stream_id,
                type,
                current_stream_position + row_number() OVER (ORDER BY ordinality)
            FROM unnest(
                ids_array,
                data_array,
                metadata_array,
                types_array
            ) WITH ORDINALITY AS t(id, data, metadata, type);

            -- update stream position
            UPDATE streams as s
                SET stream_position = current_stream_position
            WHERE
                s.id = stream_id;

            RETURN TRUE;
        END;
        $$;
    """;
}
