package io.eventdriven.buildyourowneventstore;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.buildyourowneventstore.tools.SqlInvoker.*;
import static io.eventdriven.buildyourowneventstore.JsonEventSerializer.*;

public class PgEventStore implements EventStore {

    public PgEventStore(Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

    @Override
    public void Init() {
        exectuteSql(dbConnection, createStreamsTableSql);
        exectuteSql(dbConnection, createEventsTableSql);
        exectuteSql(dbConnection, createAppendFunctionSql);
    }

    @Override
    public <TStream> void appendEvents(
        Class<TStream> streamClass,
        UUID streamId,
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
                        setStringParam(ps, 4, streamId.toString());
                        setStringParam(ps, 5, streamClass.getTypeName());
                        setLong(ps, 6, expectedVersion);
                    },
                    rs -> getBoolean(rs,"succeeded")
                );

                if (!succeeded)
                    throw new IllegalStateException("Expected version did not match the stream version!");
            }
        });
    }

    private final Connection dbConnection;

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
                    stream_version := 0;

                    INSERT INTO streams
                        (id, type, version)
                    VALUES
                        (stream_id, stream_type, stream_version);
                END IF;

                -- append event
                INSERT INTO events
                    (id, data, stream_id, type, version)
                VALUES
                    (id, data::jsonb, stream_id, type, stream_version);

                -- get stream version
                SELECT
                    version INTO stream_version
                FROM streams as s
                WHERE
                    s.id = stream_id FOR UPDATE;

                -- if stream doesn't exist - create new one with version 0
                IF stream_version IS NULL THEN
                    stream_version := 0;

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
