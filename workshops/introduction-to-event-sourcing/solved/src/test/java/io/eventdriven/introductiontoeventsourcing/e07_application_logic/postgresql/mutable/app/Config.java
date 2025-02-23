package io.eventdriven.introductiontoeventsourcing.e07_application_logic.postgresql.mutable.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventdriven.eventstores.postgresql.PostgreSQLEventStore;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.core.http.GlobalExceptionHandler;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.core.serializer.DefaultSerializer;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.postgresql.mutable.app.shoppingcarts.ShoppingCartStore;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.postgresql.mutable.app.shoppingcarts.productItems.FakeProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.postgresql.mutable.app.shoppingcarts.productItems.ProductPriceCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.web.context.annotation.ApplicationScope;
import org.springframework.web.context.annotation.RequestScope;

import javax.sql.DataSource;
import java.sql.SQLException;

@Configuration
class Config {
  @Bean
  ObjectMapper defaultJSONMapper() {
    return DefaultSerializer.mapper;
  }

  @Bean
  @RequestScope
  PostgreSQLEventStore postgreSQLEventStore(DataSource dataSource) throws SQLException {
    var store = new PostgreSQLEventStore(dataSource.getConnection());
    store.init();
    return store;
  }

  @Bean
  @ApplicationScope
  ProductPriceCalculator productPriceCalculator() {
    return FakeProductPriceCalculator.returning(100);
  }


  @Bean
  @Scope("singleton")
  public static ShoppingCartStore shoppingCartStore(PostgreSQLEventStore eventStore) {
    return new ShoppingCartStore(eventStore);
  }

  @Primary
  @Bean
  public GlobalExceptionHandler restResponseEntityExceptionHandler() {
    return new GlobalExceptionHandler();
  }
}
