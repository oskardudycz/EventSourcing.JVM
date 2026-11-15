package io.eventdriven.eventstores.testing.tools.postgresql;

public record Settings(
  String connectionString,
  String userName,
  String password
) {
}
