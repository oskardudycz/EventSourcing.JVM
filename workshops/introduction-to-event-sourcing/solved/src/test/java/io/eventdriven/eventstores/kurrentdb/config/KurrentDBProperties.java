package io.eventdriven.eventstores.kurrentdb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("kurrentdb")
public record KurrentDBProperties(
  String connectionString
) {
}
