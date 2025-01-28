package io.eventdriven.buildyourowneventstore.e02_append_events.postgresql.single_event;

import io.eventdriven.buildyourowneventstore.EventTypeMapper;
import io.eventdriven.buildyourowneventstore.e02_append_events.EventStore;

import java.sql.Connection;
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
      for (var event : events) {
        boolean succeeded = querySingleSql(
          connection,
          "SELECT append_event(?::TEXT, ?::jsonb, ?::jsonb, ?, ?::TEXT, ?, ?) AS succeeded",
          ps -> {
            setStringParam(ps, 1, UUID.randomUUID().toString());
            setStringParam(ps, 2, serialize(event));
            setStringParam(ps, 3, "{}");
            setStringParam(ps, 4, EventTypeMapper.toName(event.getClass()));
            setStringParam(ps, 5, streamId);
            setStringParam(ps, 6, streamClass.getTypeName());
            setLong(ps, 7, expectedStreamPosition);
          },
          rs -> getBoolean(rs, "succeeded")
        );

        if (!succeeded)
          throw new IllegalStateException("Expected stream position did not match the current stream position!");
      }
    });
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
    CREATE OR REPLACE FUNCTION append_event(
        id TEXT,
        data jsonb,
        metadata jsonb,
        type TEXT,
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
            VALUES
                (id, data, metadata, stream_id, type, current_stream_position);

            RETURN TRUE;
        END;
        $$;
    """;
}
