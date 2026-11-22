package io.eventdriven.core.eventstore.postgresql;

import com.zaxxer.hikari.HikariDataSource;
import io.eventdriven.testing.TestDatabases;

final class PostgreSQLTestDatabase {
    private PostgreSQLTestDatabase() {}

    static HikariDataSource dataSource() {
        var postgreSQL = TestDatabases.newPostgreSQLDatabase();
        var dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(postgreSQL.url());
        dataSource.setUsername(postgreSQL.username());
        dataSource.setPassword(postgreSQL.password());

        return dataSource;
    }
}
