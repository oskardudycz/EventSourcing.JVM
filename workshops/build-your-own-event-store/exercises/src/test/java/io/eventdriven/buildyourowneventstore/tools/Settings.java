package io.eventdriven.buildyourowneventstore.tools;

public record Settings(
  String connectionString,
  String userName,
  String password
) {
}
