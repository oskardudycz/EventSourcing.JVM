package io.eventdriven.core.commands;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageEnvelope;
import java.time.Duration;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

public class CommandHandler<Id, State, Event> {
    private static final String COMMAND_TYPE = "eventdriven.command.type";
    private static final String COMMAND_STATUS = "eventdriven.command.status";
    private static final String EVENT_COUNT = "eventdriven.command.event_count";
    private static final String EVENT_TYPES = "eventdriven.command.event_types";
    private static final String STREAM_NAME = "eventdriven.stream.name";
    private static final String STREAM_VERSION_BEFORE = "eventdriven.stream.version.before";
    private static final String STREAM_VERSION_AFTER = "eventdriven.stream.version.after";

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

    public <Command> long handle(Id id, Command command, BiFunction<Command, State, Event[]> decide) {
        return retry.invoke(() -> handleOnce(id, command, decide, null, false));
    }

    public <Command> long handleContinuingTrace(Id id, Command command, BiFunction<Command, State, Event[]> decide) {
        return retry.invoke(() -> handleOnce(id, command, decide, null, true));
    }

    public <Command> long handle(
            Id id, long expectedVersion, Command command, BiFunction<Command, State, Event[]> decide) {
        if (expectedVersion < 0)
            throw new ConcurrentModificationException(
                    "Cannot handle a command on stream %s: expected version %s is not a stream version"
                            .formatted(mapToStreamId.apply(id), expectedVersion));

        return handleOnce(id, command, decide, expectedVersion, false);
    }

    private <Command> long handleOnce(
            Id id,
            Command command,
            BiFunction<Command, State, Event[]> decide,
            Long clientVersion,
            boolean continuingTrace) {
        var streamId = mapToStreamId.apply(id);
        var commandType = command.getClass().getSimpleName();
        var events = eventsOf(id);
        var version = events.size() - 1L;

        try (MessageContext.Scope _ = continuingTrace ? scopeOf(commandType, events) : () -> {}) {
            var observation = messageContext
                    .observation("command.handle")
                    .lowCardinalityKeyValue(COMMAND_TYPE, commandType)
                    .highCardinalityKeyValue(STREAM_NAME, streamId)
                    .highCardinalityKeyValue(STREAM_VERSION_BEFORE, String.valueOf(version))
                    .start();

            try (var _ = observation.openScope()) {
                var newEvents = decide.apply(command, fold(events));
                observation
                        .highCardinalityKeyValue(EVENT_COUNT, String.valueOf(newEvents.length))
                        .highCardinalityKeyValue(EVENT_TYPES, typesOf(newEvents));

                var nextVersion = newEvents.length == 0
                        ? (clientVersion != null ? clientVersion : version)
                        : append(
                                streamId,
                                clientVersion != null
                                        ? ExpectedStreamVersion.at(clientVersion)
                                        : toExpectedVersion(version),
                                newEvents);

                observation
                        .highCardinalityKeyValue(STREAM_VERSION_AFTER, String.valueOf(nextVersion))
                        .lowCardinalityKeyValue(COMMAND_STATUS, "success");
                return nextVersion;
            } catch (RuntimeException failure) {
                observation.lowCardinalityKeyValue(COMMAND_STATUS, "failure").error(failure);
                throw failure;
            } finally {
                observation.stop();
            }
        }
    }

    private MessageContext.Scope scopeOf(String commandType, List<MessageEnvelope<Object>> events) {
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

    private List<MessageEnvelope<Object>> eventsOf(Id id) {
        return switch (eventStore.read(mapToStreamId.apply(id))) {
            case EventStore.ReadResult.StreamDoesNotExist ignored -> List.of();
            case EventStore.ReadResult.Success success -> success.events();
            case EventStore.ReadResult.UnexpectedFailure failure -> throw new RuntimeException(failure.t());
        };
    }

    @SuppressWarnings("unchecked")
    private State fold(List<MessageEnvelope<Object>> events) {
        var state = initial.get();

        for (var envelope : events) {
            state = evolve.apply(state, (Event) envelope.message());
        }

        return state;
    }

    private static String typesOf(Object[] events) {
        return Arrays.stream(events)
                .map(event -> event.getClass().getSimpleName())
                .distinct()
                .collect(Collectors.joining(","));
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
