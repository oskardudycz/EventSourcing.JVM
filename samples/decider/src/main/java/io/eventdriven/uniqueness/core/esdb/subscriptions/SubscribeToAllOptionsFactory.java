package io.eventdriven.uniqueness.core.esdb.subscriptions;

import com.eventstore.dbclient.SubscribeToAllOptions;
import com.eventstore.dbclient.SubscriptionFilter;
import io.eventdriven.uniqueness.core.serialization.EventTypeMapper;

public final class SubscribeToAllOptionsFactory {
  public static <EventType> SubscribeToAllOptions filterByType(Class<EventType> eventType) {
    return SubscribeToAllOptions.get()
      .filter(SubscriptionFilter.newBuilder().withEventTypePrefix(EventTypeMapper.toName(eventType)).build());
  }
}
