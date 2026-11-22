package io.eventdriven.core.commands;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.core.messaging.MessageContext;
import java.time.Duration;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

public class CommandHandler<Id, State, Event> {
    private static final RetryTemplate retry = new RetryTemplate(RetryPolicy.builder()
            .includes(ConcurrentModificationException.class)
            .maxRetries(2)
            .delay(Duration.ZERO)
            .build());

    private final EventStore eventStore;
    private final MessageContext messageContext;
    private final Function<Id, String> mapToStreamId;
    private final Supplier<State> initial;
    private final BiFunction<State, Event, State> evolve;

    public CommandHandler(
            EventStore eventStore,
            MessageContext messageContext,
            Function<Id, String> mapToStreamId,
            Supplier<State> initial,
            BiFunction<State, Event, State> evolve) {
        this.eventStore = eventStore;
        this.messageContext = messageContext;
        this.mapToStreamId = mapToStreamId;
        this.initial = initial;
        this.evolve = evolve;
    }

    public State get(Id id) {
        return fold(eventsOf(id));
    }

    public long handle(Id id, Function<State, Event[]> decide) {
        return retry.invoke(() -> handleOnce(id, decide, null, null));
    }

    public long handleContinuingTrace(Id id, String commandType, Function<State, Event[]> decide) {
        return retry.invoke(() -> handleOnce(id, decide, null, commandType));
    }

    public long handle(Id id, long expectedVersion, Function<State, Event[]> decide) {
        if (expectedVersion < 0)
            throw new ConcurrentModificationException(
                    "Cannot handle a command on stream %s: expected version %s is not a stream version"
                            .formatted(mapToStreamId.apply(id), expectedVersion));

        return handleOnce(id, decide, expectedVersion, null);
    }

    private long handleOnce(
            Id id, Function<State, Event[]> decide, Long clientVersion, String continuingTraceOfCommand) {
        var events = eventsOf(id);
        var version = events.size() - 1L;

        try (var _ = scopeOf(continuingTraceOfCommand, events)) {
            var newEvents = decide.apply(fold(events));

            if (newEvents.length == 0) return clientVersion != null ? clientVersion : version;

            var expectedVersion =
                    clientVersion != null ? ExpectedStreamVersion.at(clientVersion) : toExpectedVersion(version);

            return append(mapToStreamId.apply(id), expectedVersion, newEvents);
        }
    }

    private MessageContext.Scope scopeOf(String commandType, List<EventEnvelope<Object>> events) {
        if (commandType == null || events.isEmpty()) return () -> {};

        return messageContext.continuing(
                "handle %s".formatted(commandType), events.getLast().metadata());
    }

    private long append(String streamId, ExpectedStreamVersion expectedVersion, Event[] newEvents) {
        return switch (eventStore.append(streamId, expectedVersion, (Object[]) newEvents)) {
            case EventStore.AppendResult.Success success -> success.nextStreamVersion();
            case EventStore.AppendResult.StreamAlreadyExists alreadyExists ->
                throw new ConcurrentModificationException(
                        "Cannot append to stream %s: expected no stream, but it is at %s"
                                .formatted(streamId, describe(alreadyExists.actual())));
            case EventStore.AppendResult.Conflict conflict ->
                throw new ConcurrentModificationException("Cannot append to stream %s: expected %s, but it is at %s"
                        .formatted(streamId, describe(conflict.expected()), describe(conflict.actual())));
            case EventStore.AppendResult.UnexpectedFailure failure -> throw new RuntimeException(failure.t());
        };
    }

    private List<EventEnvelope<Object>> eventsOf(Id id) {
        return switch (eventStore.read(mapToStreamId.apply(id))) {
            case EventStore.ReadResult.StreamDoesNotExist ignored -> List.of();
            case EventStore.ReadResult.Success success -> success.events();
            case EventStore.ReadResult.UnexpectedFailure failure -> throw new RuntimeException(failure.t());
        };
    }

    @SuppressWarnings("unchecked")
    private State fold(List<EventEnvelope<Object>> events) {
        var state = initial.get();

        for (var envelope : events) {
            state = evolve.apply(state, (Event) envelope.data());
        }

        return state;
    }

    private static ExpectedStreamVersion toExpectedVersion(long version) {
        return version < 0 ? ExpectedStreamVersion.noStream() : ExpectedStreamVersion.at(version);
    }

    private static String describe(long version) {
        return version < 0 ? "no stream" : "version %s".formatted(version);
    }

    private static String describe(ExpectedStreamVersion expected) {
        return switch (expected) {
            case ExpectedStreamVersion.NoStream ignored -> "no stream";
            case ExpectedStreamVersion.StreamExists ignored -> "stream exists";
            case ExpectedStreamVersion.Any ignored -> "any version";
            case ExpectedStreamVersion.At at -> describe(at.version());
        };
    }
}
