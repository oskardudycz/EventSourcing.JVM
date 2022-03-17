package io.eventdriven.ecommerce.api.config;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import com.eventstore.dbclient.ParseError;
import io.eventdriven.ecommerce.api.backgroundworkers.EventStoreDBSubscriptionBackgroundWorker;
import io.eventdriven.ecommerce.core.events.IEventBus;
import io.eventdriven.ecommerce.core.subscriptions.EventStoreDBSubscriptionCheckpointRepository;
import io.eventdriven.ecommerce.core.subscriptions.ISubscriptionCheckpointRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class EventStoreDBConfig {

  @Bean
  @Scope("singleton")
  EventStoreDBClient eventStoreDBClient() throws ParseError {
    EventStoreDBClientSettings settings = EventStoreDBConnectionString.parse("esdb://localhost:2113?tls=false");

    return EventStoreDBClient.create(settings);
  }

  @Bean
  public EventStoreDBSubscriptionBackgroundWorker eventStoreDBSubscriptionBackgroundWorker(
    EventStoreDBClient eventStore,
    ISubscriptionCheckpointRepository subscriptionCheckpointRepository,
    IEventBus eventBus
  ) {
    return new EventStoreDBSubscriptionBackgroundWorker(eventStore, subscriptionCheckpointRepository, eventBus);
  }

  @Bean
  public ISubscriptionCheckpointRepository subscriptionCheckpointRepository(EventStoreDBClient eventStore) {
    return new EventStoreDBSubscriptionCheckpointRepository(eventStore);
  }
}
