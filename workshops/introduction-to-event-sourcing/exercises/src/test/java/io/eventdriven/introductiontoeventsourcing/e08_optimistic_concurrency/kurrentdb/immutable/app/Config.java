package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.kurrentdb.immutable.app;

import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.KurrentDBClientSettings;
import io.kurrent.dbclient.KurrentDBConnectionString;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.kurrentdb.core.eventstore.KurrentDBEventStore;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.core.http.GlobalExceptionHandler;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.core.serializer.DefaultSerializer;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.kurrentdb.immutable.app.shoppingcarts.ShoppingCartStore;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.kurrentdb.immutable.app.shoppingcarts.productItems.FakeProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.kurrentdb.immutable.app.shoppingcarts.productItems.ProductPriceCalculator;
import io.eventdriven.eventstores.kurrentdb.config.KurrentDBProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.web.context.annotation.ApplicationScope;

@Configuration
@EnableConfigurationProperties(KurrentDBProperties.class)
class Config {
  @Bean
  JsonMapper defaultJSONMapper() {
    return DefaultSerializer.mapper;
  }

  @Bean
  @Scope("singleton")
  KurrentDBClient kurrentDBClient(KurrentDBProperties properties) {
    try {
      KurrentDBClientSettings settings = KurrentDBConnectionString.parseOrThrow(properties.connectionString());

      return KurrentDBClient.create(settings);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  @Bean
  @Scope("singleton")
  KurrentDBEventStore eventStore(KurrentDBClient kurrentDBClient, ObjectMapper mapper) {
    return new KurrentDBEventStore(kurrentDBClient, mapper);
  }

  @Bean
  @ApplicationScope
  ProductPriceCalculator productPriceCalculator() {
    return FakeProductPriceCalculator.returning(100);
  }


  @Bean
  @Scope("singleton")
  public static ShoppingCartStore shoppingCartStore(KurrentDBEventStore eventStore) {
    return new ShoppingCartStore(eventStore);
  }

  @Primary
  @Bean
  public GlobalExceptionHandler restResponseEntityExceptionHandler (){
    return new GlobalExceptionHandler ();
  }
}
