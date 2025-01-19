package io.eventdriven.buildyourowneventstore.e04_event_store_methods.postgresql;

import io.eventdriven.buildyourowneventstore.EventTypeMapper;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.EventStore;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.buildyourowneventstore.JsonEventSerializer.deserialize;
import static io.eventdriven.buildyourowneventstore.JsonEventSerializer.serialize;
import static io.eventdriven.buildyourowneventstore.tools.SqlInvoker.*;
import static io.eventdriven.buildyourowneventstore.tools.SqlInvoker.getString;

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
    Long expectedVersion,
    Object... events
  ) {

    runInTransaction(dbConnection, connection ->
    {
      for (var event : events) {
        boolean succeeded = querySingleSql(
          connection,
          "SELECT append_event(?::uuid, ?::jsonb, ?, ?::uuid, ?, ?) AS succeeded",
          ps -> {
            setStringParam(ps, 1, UUID.randomUUID().toString());
            setStringParam(ps, 2, serialize(event));
            setStringParam(ps, 3, EventTypeMapper.toName(event.getClass()));
            setStringParam(ps, 4, streamId);
            setStringParam(ps, 5, streamClass.getTypeName());
            setLong(ps, 6, expectedVersion);
          },
          rs -> getBoolean(rs, "succeeded")
        );

        if (!succeeded)
          throw new IllegalStateException("Expected version did not match the stream version!");
      }
    });
  }

  @Override
  public <Stream> List<Object> getEvents(
    Class<Stream> streamClass,
    String streamId,
    Long atStreamVersion,
    LocalDateTime atTimestamp
  ) {
    var atStreamCondition = atStreamVersion != null ? "AND version <= ?" : "";
    var atTimestampCondition = atTimestamp != null ? "AND created <= ?" : "";

    var getStreamSql = """
            SELECT id, data, stream_id, type, version, created
            FROM events
            WHERE stream_id = ?::uuid
            """
      + atStreamCondition
      + atTimestampCondition
      + " ORDER BY version";

    return querySql(
      dbConnection,
      getStreamSql,
      ps -> {
        var index = 1;
        setStringParam(ps, index++, streamId.toString());
        if(atStreamVersion != null)
          setLong(ps, index++, atStreamVersion);
        if(atTimestamp != null)
          setLocalDateTime(ps, index, atTimestamp);
      },
      rs -> {
        var eventTypeName = getString(rs,"type");
        return deserialize(
          EventTypeMapper.toClass(eventTypeName).get(),
          eventTypeName,
          getString(rs,"data")
        ).get();
      }
    );
  }

  private final String createStreamsTableSql = """
        CREATE TABLE IF NOT EXISTS streams(
            id             UUID                      NOT NULL    PRIMARY KEY,
            type           TEXT                      NOT NULL,
            version        BIGINT                    NOT NULL
        );
        """;

  private final String createEventsTableSql = """
        CREATE TABLE IF NOT EXISTS events(
              id             UUID                      NOT NULL    PRIMARY KEY,
              data           JSONB                     NOT NULL,
              stream_id      UUID                      NOT NULL,
              type           TEXT                      NOT NULL,
              version        BIGINT                    NOT NULL,
              created        timestamp with time zone  NOT NULL    default (now()),
              FOREIGN KEY(stream_id) REFERENCES streams(id),
              CONSTRAINT events_stream_and_version UNIQUE(stream_id, version)
        );
        """;

  private final String createAppendFunctionSql = """
        CREATE OR REPLACE FUNCTION append_event(
            id uuid,
            data jsonb,
            type text,
            stream_id uuid,
            stream_type text,
            expected_stream_version bigint default null
        ) RETURNS boolean
            LANGUAGE plpgsql
            AS $$
            DECLARE
                stream_version int;
            BEGIN
                -- get stream version
                SELECT
                    version INTO stream_version
                FROM streams as s
                WHERE
                    s.id = stream_id FOR UPDATE;

                -- if stream doesn't exist - create new one with version 0
                IF stream_version IS NULL THEN
                    stream_version := -1;

                    INSERT INTO streams
                        (id, type, version)
                    VALUES
                        (stream_id, stream_type, stream_version);
                END IF;

                -- check optimistic concurrency
                IF expected_stream_version IS NOT NULL AND stream_version != expected_stream_version THEN
                    RETURN FALSE;
                END IF;

                -- increment event_version
                stream_version := stream_version + 1;

                -- append event
                INSERT INTO events
                    (id, data, stream_id, type, version)
                VALUES
                    (id, data::jsonb, stream_id, type, stream_version);

                -- update stream version
                UPDATE streams as s
                    SET version = stream_version
                WHERE
                    s.id = stream_id;

                RETURN TRUE;
            END;
            $$;
        """;
}
