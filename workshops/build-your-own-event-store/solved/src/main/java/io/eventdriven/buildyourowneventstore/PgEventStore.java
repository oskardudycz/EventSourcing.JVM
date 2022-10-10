package io.eventdriven.buildyourowneventstore;

import java.sql.Connection;
import java.sql.SQLException;

import static io.eventdriven.buildyourowneventstore.tools.SqlInvoker.*;

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
}
