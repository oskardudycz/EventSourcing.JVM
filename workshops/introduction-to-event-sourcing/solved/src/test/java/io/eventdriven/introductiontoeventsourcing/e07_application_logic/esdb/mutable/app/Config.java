package io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mutable.app;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.eventStoreDB.EventStore;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.http.GlobalExceptionHandler;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.serializer.DefaultSerializer;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mutable.app.shoppingcarts.ShoppingCartStore;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mutable.app.shoppingcarts.productItems.FakeProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mutable.app.shoppingcarts.productItems.ProductPriceCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.web.context.annotation.ApplicationScope;

@Configuration
class Config {
  @Bean
  ObjectMapper defaultJSONMapper() {
    return DefaultSerializer.mapper;
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
  @Scope("singleton")
  EventStore eventStore(EventStoreDBClient eventStoreDBClient, ObjectMapper mapper) {
    return new EventStore(eventStoreDBClient, mapper);
  }

  @Bean
  @ApplicationScope
  ProductPriceCalculator productPriceCalculator() {
    return FakeProductPriceCalculator.returning(100);
  }


  @Bean
  @Scope("singleton")
  public static ShoppingCartStore shoppingCartStore(EventStore eventStore) {
    return new ShoppingCartStore(eventStore);
  }

  @Primary
  @Bean
  public GlobalExceptionHandler restResponseEntityExceptionHandler() {
    return new GlobalExceptionHandler();
  }
}
