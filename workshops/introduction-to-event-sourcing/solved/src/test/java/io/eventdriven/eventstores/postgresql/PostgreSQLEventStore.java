package io.eventdriven.eventstores.postgresql;

import io.eventdriven.eventstores.EventStore;
import io.eventdriven.eventstores.EventTypeMapper;
import io.eventdriven.eventstores.JsonEventSerializer;
import io.eventdriven.eventstores.StreamName;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.eventstores.JsonEventSerializer.deserialize;
import static io.eventdriven.eventstores.postgresql.tools.SqlInvoker.*;


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
  public AppendResult appendToStream(
    StreamName streamName,
    Long expectedStreamPosition,
    List<Object> events
  ) {
    return runInTransaction(dbConnection, connection ->
    {
      var ids = events.stream()
        .map(_ -> UUID.randomUUID().toString())
        .toArray(String[]::new);

      var eventData = events.stream()
        .map(JsonEventSerializer::serialize)
        .toArray(String[]::new);

      var eventMetadata = events.stream()
        .map(_ -> "{}")
        .toArray(String[]::new);

      var eventTypes = events.stream()
        .map(event -> EventTypeMapper.toName(event.getClass()))
        .toArray(String[]::new);

      boolean succeeded = querySingleSql(
        connection,
        "SELECT append_to_stream(?::text[], ?::jsonb[], ?::jsonb[], ?::text[], ?::text, ?, ?) AS succeeded",
        ps -> {
          setArrayOf(dbConnection, ps, 1, "text", ids);
          setArrayOf(dbConnection, ps, 2, "jsonb", eventData);
          setArrayOf(dbConnection, ps, 3, "jsonb", eventMetadata);
          setArrayOf(dbConnection, ps, 4, "text", eventTypes);
          setStringParam(ps, 5, streamName.streamId());
          setStringParam(ps, 6, streamName.streamType());
          setLong(ps, 7, expectedStreamPosition);
        },
        rs -> getBoolean(rs, "succeeded")
      );

      if (!succeeded)
        throw new InvalidExpectedStreamPositionException(streamName.toString(), expectedStreamPosition);

      var nextExpectedPosition = (expectedStreamPosition != null? expectedStreamPosition: 0)+ events.size();

      return new AppendResult(nextExpectedPosition);
    });
  }

  @Override
  public ReadStreamResult readStream(StreamName streamName) {
    return readStream(streamName, null, null);
  }

  public ReadStreamResult readStream(
    StreamName streamName,
    Long atStreamPosition,
    LocalDateTime atTimestamp
  ) {
    var atStreamCondition = atStreamPosition != null ? "AND stream_position <= ?" : "";
    var atTimestampCondition = atTimestamp != null ? "AND created <= ?" : "";

    var getStreamSql = """
      SELECT id, data, stream_id, type, stream_position, created
      FROM events
      WHERE stream_id = ?
      """
      + atStreamCondition
      + atTimestampCondition
      + " ORDER BY stream_position";

    var events = querySql(
      dbConnection,
      getStreamSql,
      ps -> {
        var index = 1;
        setStringParam(ps, index++, streamName.streamId());
        if (atStreamPosition != null)
          setLong(ps, index++, atStreamPosition);
        if (atTimestamp != null)
          setLocalDateTime(ps, index, atTimestamp);
      },
      rs -> {
        var eventTypeName = getString(rs, "type");
        return deserialize(
          EventTypeMapper.toClass(eventTypeName).get(),
          eventTypeName,
          getString(rs, "data")
        ).get();
      }
    );

    // TODO: This should read the position from Streams table
    return new ReadStreamResult(events.size(), events);
  }

  private final String createStreamsTableSql = """
    CREATE TABLE IF NOT EXISTS streams(
        id               TEXT                      NOT NULL    PRIMARY KEY,
        type             TEXT                      NOT NULL,
        stream_position  BIGINT                    NOT NULL
    );
    """;

  private final String createEventsTableSql = """
    CREATE SEQUENCE IF NOT EXISTS global_event_position;

    CREATE TABLE IF NOT EXISTS events(
          stream_id        TEXT                      NOT NULL,
          stream_position  BIGINT                    NOT NULL,
          global_position  BIGINT                    DEFAULT nextval('global_event_position'),
          id               TEXT                      NOT NULL,
          data             JSONB                     NOT NULL,
          metadata         JSONB                     DEFAULT '{}',
          type             TEXT                      NOT NULL,
          created          timestamp with time zone  NOT NULL    default (now()),
          FOREIGN KEY(stream_id) REFERENCES streams(id),
          PRIMARY KEY (stream_id, stream_position)
    );
    """;

  private final String createAppendFunctionSql = """
    CREATE OR REPLACE FUNCTION append_to_stream(
        ids_array TEXT[],
        data_array jsonb[],
        metadata_array jsonb[],
        types_array text[],
        stream_id TEXT,
        stream_type text,
        expected_stream_position bigint default null
    ) RETURNS boolean
        LANGUAGE plpgsql
        AS $$
        DECLARE
            current_stream_position int;
            updated_rows int;
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

            -- update stream position
            UPDATE streams as s
                SET stream_position = current_stream_position
            WHERE
                s.id = stream_id;

            get diagnostics updated_rows = row_count;

            IF updated_rows = 0 THEN
                RETURN FALSE;
            END IF;

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

            RETURN TRUE;
        END;
        $$;
    """;
}
