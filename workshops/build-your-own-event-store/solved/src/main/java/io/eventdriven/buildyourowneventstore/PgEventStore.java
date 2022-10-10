package io.eventdriven.buildyourowneventstore;

import java.sql.Connection;
import java.sql.SQLException;

public class PgEventStore implements EventStore {
    private final Connection dbConnection;

    public PgEventStore(Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

    @Override
    public void Init() {
        CreateStreamsTable();
    }

    private void CreateStreamsTable() {
        final String createStreamsTableSql =
            """
            CREATE TABLE IF NOT EXISTS streams(
                id             UUID                      NOT NULL    PRIMARY KEY,
                type           TEXT                      NOT NULL,
                version        BIGINT                    NOT NULL
            );
            """;
        try (var statement = dbConnection.createStatement()) {
            statement.execute(createStreamsTableSql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
