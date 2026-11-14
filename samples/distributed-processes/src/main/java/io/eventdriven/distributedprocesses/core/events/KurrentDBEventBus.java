package io.eventdriven.distributedprocesses.core.events;

import io.kurrent.dbclient.KurrentDBClient;
import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.retries.RetryPolicy;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.core.kurrentdb.subscriptions.KurrentDBSubscription.subscribeToStream;
import static io.eventdriven.distributedprocesses.core.serialization.EventSerializer.deserializeEvent;

public class KurrentDBEventBus implements EventBus {
  private static final String EventStreamId = "_events-all";
  private final KurrentDBClient kurrentDBClient;
  private final EventStore eventStore;
  private final RetryPolicy retryPolicy;
  private final Supplier<String> currentCorrelationId;
  private final Supplier<String> currentCausationId;

  public KurrentDBEventBus(
    KurrentDBClient kurrentDBClient,
    EventStore eventStore,
    RetryPolicy retryPolicy,
    Supplier<String> currentCorrelationId,
    Supplier<String> currentCausationId
  ) {
    this.kurrentDBClient = kurrentDBClient;
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
        new EventEnvelope<>(event, new EventMetadata(currentCorrelationId.get(), currentCausationId.get()))
      );

      if (!(result instanceof EventStore.AppendResult.UnexpectedFailure))
        ack.accept(result);
    });
  }

  @Override
  public final void subscribe(Consumer<EventEnvelope<Object>>... handlers) {
    subscribeToStream(kurrentDBClient, EventStreamId, (subscription, resolvedEvent) -> {
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
