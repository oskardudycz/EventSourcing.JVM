package io.eventdriven.distributedprocesses.core.commands;

import io.eventdriven.distributedprocesses.core.eventstore.EventStore;
import io.eventdriven.distributedprocesses.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import java.time.Duration;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

public class CommandHandler<Id, State, Command, Event> {
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
    private final BiFunction<Command, State, Event[]> decide;

    public CommandHandler(
            EventStore eventStore,
            MessageContext messageContext,
            Function<Id, String> mapToStreamId,
            Supplier<State> initial,
            BiFunction<State, Event, State> evolve,
            BiFunction<Command, State, Event[]> decide) {
        this.eventStore = eventStore;
        this.messageContext = messageContext;
        this.mapToStreamId = mapToStreamId;
        this.initial = initial;
        this.evolve = evolve;
        this.decide = decide;
    }

    public State get(Id id) {
        return fold(eventsOf(id));
    }

    public long handle(Id id, Command command) {
        return retry.invoke(() -> handleOnce(id, command, null, false));
    }

    public long handleContinuingTrace(Id id, Command command) {
        return retry.invoke(() -> handleOnce(id, command, null, true));
    }

    public long handle(Id id, Command command, long expectedVersion) {
        if (expectedVersion < 0)
            throw new ConcurrentModificationException(
                    "Cannot handle %s on stream %s: expected version %s is not a stream version"
                            .formatted(command.getClass().getSimpleName(), mapToStreamId.apply(id), expectedVersion));

        return handleOnce(id, command, expectedVersion, false);
    }

    private long handleOnce(Id id, Command command, Long clientVersion, boolean continuingTrace) {
        var events = eventsOf(id);
        var version = events.size() - 1L;

        try (var _ = scopeOf(command, events, continuingTrace)) {
            var newEvents = decide.apply(command, fold(events));

            if (newEvents.length == 0) return clientVersion != null ? clientVersion : version;

            var expectedVersion =
                    clientVersion != null ? ExpectedStreamVersion.at(clientVersion) : toExpectedVersion(version);

            return append(mapToStreamId.apply(id), expectedVersion, newEvents);
        }
    }

    private MessageContext.Scope scopeOf(Command command, List<EventEnvelope<Object>> events, boolean continuingTrace) {
        if (!continuingTrace || events.isEmpty()) return () -> {};

        return messageContext.continuing(
                "handle %s".formatted(command.getClass().getSimpleName()),
                events.getLast().metadata());
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
