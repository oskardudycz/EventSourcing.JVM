package io.eventdriven.distributedprocesses.core.commands;

import io.kurrent.dbclient.KurrentDBClient;
import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import org.springframework.core.retry.RetryTemplate;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.core.kurrentdb.subscriptions.KurrentDBSubscription.subscribeToStream;
import static io.eventdriven.distributedprocesses.core.serialization.EventSerializer.deserializeCommand;

public class KurrentDBCommandBus implements CommandBus {
  private static final String commandStreamId = "_commands-all";
  private final KurrentDBClient kurrentDBClient;
  private final EventStore eventStore;
  private final RetryTemplate retryTemplate;
  private final Supplier<String> currentCorrelationId;
  private final Supplier<String> currentCausationId;

  public KurrentDBCommandBus(
    KurrentDBClient kurrentDBClient,
    EventStore eventStore,
    RetryTemplate retryTemplate,
    Supplier<String> currentCorrelationId,
    Supplier<String> currentCausationId
  ) {
    this.kurrentDBClient = kurrentDBClient;
    this.eventStore = eventStore;
    this.retryTemplate = retryTemplate;
    this.currentCorrelationId = currentCorrelationId;
    this.currentCausationId = currentCausationId;
  }

  @Override
  public <Command> EventStore.AppendResult schedule(Command command) {
    return retryTemplate.invoke(() -> {
      var result = eventStore.append(
        commandStreamId,
        new CommandEnvelope<>(command, new CommandMetadata(currentCorrelationId.get(), currentCausationId.get()))
      );

      if (result instanceof EventStore.AppendResult.UnexpectedFailure)
        throw new IllegalStateException("Unexpected failure while appending: %s".formatted(result));

      return result;
    });
  }

  @Override
  public void subscribe(Consumer<CommandEnvelope<Object>>... handlers) {
    subscribeToStream(kurrentDBClient, commandStreamId, (subscription, resolvedEvent) -> {
      var commandEnvelope = deserializeCommand(resolvedEvent);

      if (commandEnvelope.isEmpty()) {
        return;
      }

      for (var handler : handlers) {
        handler.accept(commandEnvelope.get());
      }
    });
  }
}
