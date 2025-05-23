package io.eventdriven.eventstores.testing.tools.postgresql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class PostgreSQLDbConnectionProvider {
  public static Connection getFreshDbConnection() {
    // get the test class name that will be used as POSTGRES schema
    var testClassName = StackWalker
      .getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
      .getCallerClass()
      .getName()
      .replace(".", "_");

    // each test will have its own schema name to run have data isolation and not interfere other tests
    Connection connection = null;
    try {
      connection = DriverManager.getConnection(
        Settings.connectionString + "?currentSchema=%s".formatted(testClassName),
        Settings.userName,
        Settings.password
      );
      try (var statement = connection.createStatement()) {
        statement.execute("DROP SCHEMA IF EXISTS %1$s CASCADE; CREATE SCHEMA %1$s".formatted(testClassName));
      }

      return connection;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
