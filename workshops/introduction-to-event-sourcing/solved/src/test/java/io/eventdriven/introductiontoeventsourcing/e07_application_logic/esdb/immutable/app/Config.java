package io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.serialization.EventSerializer;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.pricing.ProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.pricing.RandomProductPriceCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.context.annotation.ApplicationScope;

@Configuration
class Config {
  @Bean
  ObjectMapper defaultJSONMapper() {
    return EventSerializer.mapper;
  }

  @Bean
  @Scope("singleton")
  EventStoreDBClient eventStoreDBClient(@Value("${esdb.connectionstring}") String connectionString) {
    try {
      EventStoreDBClientSettings settings = EventStoreDBConnectionString.parseOrThrow(connectionString);

      return EventStoreDBClient.create(settings);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  @Bean
  @ApplicationScope
  ProductPriceCalculator productPriceCalculator() {
    return new RandomProductPriceCalculator();
  }
}
