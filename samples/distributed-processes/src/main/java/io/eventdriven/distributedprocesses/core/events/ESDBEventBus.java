package io.eventdriven.distributedprocesses.core.events;

import static io.eventdriven.distributedprocesses.core.esdb.subscriptions.ESDBSubscription.subscribeToStream;
import static io.eventdriven.distributedprocesses.core.serialization.EventSerializer.deserializeEvent;

import com.eventstore.dbclient.EventStoreDBClient;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.retries.RetryPolicy;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ESDBEventBus implements EventBus {
  private static final String EventStreamId = "_events-all";
  private final EventStoreDBClient eventStoreDBClient;
  private final EventStore eventStore;
  private final RetryPolicy retryPolicy;
  private final Supplier<String> currentCorrelationId;
  private final Supplier<String> currentCausationId;

  public ESDBEventBus(
      EventStoreDBClient eventStoreDBClient,
      EventStore eventStore,
      RetryPolicy retryPolicy,
      Supplier<String> currentCorrelationId,
      Supplier<String> currentCausationId) {
    this.eventStoreDBClient = eventStoreDBClient;
    this.eventStore = eventStore;
    this.retryPolicy = retryPolicy;
    this.currentCorrelationId = currentCorrelationId;
    this.currentCausationId = currentCausationId;
  }

  @Override
  public <Event> EventStore.AppendResult publish(Event event) {
    return retryPolicy.run(ack -> {
      var result = eventStore.append(
          EventStreamId,
          new EventEnvelope<>(
              event, new EventMetadata(currentCorrelationId.get(), currentCausationId.get())));

      if (!(result instanceof EventStore.AppendResult.UnexpectedFailure)) ack.accept(result);
    });
  }

  @Override
  public final void subscribe(Consumer<EventEnvelope<Object>>... handlers) {
    subscribeToStream(eventStoreDBClient, EventStreamId, (subscription, resolvedEvent) -> {
      var eventEnvelope = deserializeEvent(resolvedEvent);

      if (eventEnvelope.isEmpty()) {
        return;
      }

      for (var handler : handlers) {
        handler.accept(eventEnvelope.get());
      }
    });
  }
}
