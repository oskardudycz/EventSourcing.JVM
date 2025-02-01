package io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.immutable.app;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.core.eventStoreDB.EventStore;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.core.http.GlobalExceptionHandler;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.core.serializer.DefaultSerializer;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.immutable.app.shoppingcarts.ShoppingCartStore;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.immutable.app.shoppingcarts.productItems.FakeProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.immutable.app.shoppingcarts.productItems.ProductPriceCalculator;
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
  EventStoreDBClient eventStoreDBClient(@Value("${mongodb.connectionstring}") String connectionString) {
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
  public GlobalExceptionHandler restResponseEntityExceptionHandler (){
    return new GlobalExceptionHandler();
  }
}
