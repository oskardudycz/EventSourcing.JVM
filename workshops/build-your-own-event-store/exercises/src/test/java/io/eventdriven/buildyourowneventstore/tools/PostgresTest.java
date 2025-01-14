package io.eventdriven.buildyourowneventstore.tools;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.SQLException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PostgresTest {
  protected static Connection dbConnection;
  protected static PostgresSchemaProvider schemaProvider;

  @BeforeAll
  public void setupConnection() {
    dbConnection = PostgresDbConnectionProvider.getFreshDbConnection();
    schemaProvider = new PostgresSchemaProvider(dbConnection);
  }

  @AfterAll
  public void tearDown() throws SQLException {
    dbConnection.close();
  }
}
