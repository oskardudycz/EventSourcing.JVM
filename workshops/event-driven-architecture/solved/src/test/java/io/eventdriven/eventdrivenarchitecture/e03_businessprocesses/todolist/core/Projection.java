package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;

import java.util.UUID;

public class Projection<Document> {
  private final Database database;

  public Projection(Database database) {
    this.database = database;
  }

}
