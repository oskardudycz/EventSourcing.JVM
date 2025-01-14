package io.eventdriven.buildyourowneventstore.e01_storage.postgresql;

import io.eventdriven.buildyourowneventstore.e01_storage.EventStore;

import java.sql.Connection;

import static io.eventdriven.buildyourowneventstore.tools.SqlInvoker.exectuteSql;

public class PostgreSQLEventStore implements EventStore {
  private final Connection dbConnection;

  public PostgreSQLEventStore(Connection dbConnection) {
    this.dbConnection = dbConnection;
  }

  @Override
  public void init() {
    exectuteSql(dbConnection, "Provide your create table SQL statements here");
  }
}
