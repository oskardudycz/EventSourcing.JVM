package io.eventdriven.eventstores.mongodb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mongodb")
public record MongoDBProperties(String connectionString) {}
