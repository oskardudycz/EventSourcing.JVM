package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.immutable.app;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.core.eventStoreDB.EventStore;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.immutable.app.shoppingcarts.ShoppingCart;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.immutable.app.shoppingcarts.ShoppingCartStore;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.immutable.app.shoppingcarts.productItems.FakeProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.immutable.app.shoppingcarts.productItems.ProductPriceCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.context.annotation.ApplicationScope;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Configuration
class Config {
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
  EventStore eventStore(EventStoreDBClient eventStoreDBClient) {
    return new EventStore(eventStoreDBClient);
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
}
