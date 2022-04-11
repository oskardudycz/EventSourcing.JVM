package io.eventdriven.ecommerce.core.config;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import io.eventdriven.ecommerce.api.backgroundworkers.EventStoreDBSubscriptionBackgroundWorker;
import io.eventdriven.ecommerce.core.events.EventBus;
import io.eventdriven.ecommerce.core.subscriptions.EventStoreDBSubscriptionCheckpointRepository;
import io.eventdriven.ecommerce.core.subscriptions.SubscriptionCheckpointRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
class EventStoreDBConfig {
  @Bean
  @Scope("singleton")
  EventStoreDBClient eventStoreDBClient(@Value("${esdb.connectionstring}") String connectionString) {
    try {
      EventStoreDBClientSettings settings = EventStoreDBConnectionString.parse(connectionString);

      return EventStoreDBClient.create(settings);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  @Bean
  EventStoreDBSubscriptionBackgroundWorker eventStoreDBSubscriptionBackgroundWorker(
    EventStoreDBClient eventStore,
    SubscriptionCheckpointRepository subscriptionCheckpointRepository,
    EventBus eventBus
  ) {
    return new EventStoreDBSubscriptionBackgroundWorker(eventStore, subscriptionCheckpointRepository, eventBus);
  }

  @Bean
  SubscriptionCheckpointRepository subscriptionCheckpointRepository(EventStoreDBClient eventStore) {
    return new EventStoreDBSubscriptionCheckpointRepository(eventStore);
  }
}
