package io.eventdriven.ecommerce.core.config;

import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.KurrentDBClientSettings;
import io.kurrent.dbclient.KurrentDBConnectionString;
import io.eventdriven.ecommerce.api.backgroundworkers.KurrentDBSubscriptionBackgroundWorker;
import io.eventdriven.ecommerce.core.events.EventBus;
import io.eventdriven.ecommerce.core.subscriptions.KurrentDBSubscriptionCheckpointRepository;
import io.eventdriven.ecommerce.core.subscriptions.SubscriptionCheckpointRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
class KurrentDBConfig {
  @Bean
  @Scope("singleton")
  KurrentDBClient kurrentDBClient(@Value("${kurrentdb.connectionstring}") String connectionString) {
    try {
      KurrentDBClientSettings settings = KurrentDBConnectionString.parseOrThrow(connectionString);

      return KurrentDBClient.create(settings);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  @Bean
  KurrentDBSubscriptionBackgroundWorker kurrentDBSubscriptionBackgroundWorker(
    KurrentDBClient eventStore,
    SubscriptionCheckpointRepository subscriptionCheckpointRepository,
    EventBus eventBus
  ) {
    return new KurrentDBSubscriptionBackgroundWorker(eventStore, subscriptionCheckpointRepository, eventBus);
  }

  @Bean
  SubscriptionCheckpointRepository subscriptionCheckpointRepository(KurrentDBClient eventStore) {
    return new KurrentDBSubscriptionCheckpointRepository(eventStore);
  }
}
