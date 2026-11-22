package io.eventdriven.core.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.core.eventstore.Subscription;
import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.processing.MessageProcessor;
import io.eventdriven.testing.messaging.TestMessages;
import io.eventdriven.testing.tracing.TestTracing;
import io.micrometer.tracing.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class CommandHandlerTests {
    private final TestTracing tracing = new TestTracing();
    private final MessageContext messageContext = tracing.messageContext();
    private final InMemoryEventStore inMemoryEventStore =
            new InMemoryEventStore(messageContext, TestMessages.serializerTrustingPackageOf(CommandHandlerTests.class));
    private final CompetingWriterEventStore eventStore = new CompetingWriterEventStore(inMemoryEventStore);
    private final List<Counter> statesDecidedOn = new ArrayList<>();
    private final CommandHandler<UUID, Counter, CounterEvent> handler = new CommandHandler<>(
            eventStore, messageContext, CommandHandlerTests::mapToStreamId, Counter::initial, Counter::evolve);

    private final UUID counterId = UUID.randomUUID();

    @Test
    public void handlingACommandFoldsTheStreamAndAppendsWithTheVersionRead() {
        var afterOpen = handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);

        var afterIncrement = handler.handle(counterId, new CounterCommand.Increment(3), this::decide);

        assertThat(afterOpen).isEqualTo(0);
        assertThat(afterIncrement).isEqualTo(1);
        assertThat(statesDecidedOn).containsExactly(Counter.initial(), new Counter("clicks", 0));
        assertThat(eventsIn(counterId))
                .containsExactly(new CounterEvent.Opened("clicks"), new CounterEvent.Incremented(3));
    }

    @Test
    public void aCommandThatDecidesNothingAppendsNothing() {
        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);

        var version = handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);

        assertThat(version).isEqualTo(0);
        assertThat(eventsIn(counterId)).hasSize(1);
    }

    @Test
    public void aCommandOnAnUnknownStreamThatDecidesNothingReportsNoStream() {
        var version = handler.handle(counterId, new CounterCommand.Increment(0), this::decide);

        assertThat(version).isEqualTo(-1);
        assertThat(eventsIn(counterId)).isEmpty();
    }

    @Test
    public void aConflictWithoutAClientVersionIsRetriedUpToThreeAttempts() {
        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);
        statesDecidedOn.clear();
        eventStore.competeBeforeTheNextAppends(2);

        var version = handler.handle(counterId, new CounterCommand.Increment(10), this::decide);

        assertThat(statesDecidedOn).hasSize(3);
        assertThat(version).isEqualTo(3);
        assertThat(handler.get(counterId).total()).isEqualTo(12);
    }

    @Test
    public void aThirdConflictWithoutAClientVersionFailsTheCommand() {
        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);
        statesDecidedOn.clear();
        eventStore.competeBeforeTheNextAppends(3);

        assertThatThrownBy(() -> handler.handle(counterId, new CounterCommand.Increment(10), this::decide))
                .isInstanceOf(ConcurrentModificationException.class)
                .hasMessageContaining(mapToStreamId(counterId));

        assertThat(statesDecidedOn).hasSize(3);
        assertThat(handler.get(counterId).total()).isEqualTo(3);
    }

    @Test
    public void aConflictWithAClientVersionIsNotRetried() {
        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);
        handler.handle(counterId, new CounterCommand.Increment(1), this::decide);
        statesDecidedOn.clear();

        assertThatThrownBy(() -> handler.handle(counterId, 0, new CounterCommand.Increment(10), this::decide))
                .isInstanceOf(ConcurrentModificationException.class);

        assertThat(statesDecidedOn).hasSize(1);
        assertThat(handler.get(counterId).total()).isEqualTo(1);
    }

    @Test
    public void aCommandWithTheCurrentClientVersionIsAppendedAndReturnsTheNextVersion() {
        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);

        var version = handler.handle(counterId, 0, new CounterCommand.Increment(10), this::decide);

        assertThat(version).isEqualTo(1);
        assertThat(handler.get(counterId).total()).isEqualTo(10);
    }

    @Test
    public void aCommandWithAClientVersionThatDecidesNothingReturnsThatVersion() {
        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);
        handler.handle(counterId, new CounterCommand.Increment(1), this::decide);

        var version = handler.handle(counterId, 1, new CounterCommand.Increment(0), this::decide);

        assertThat(version).isEqualTo(1);
        assertThat(eventsIn(counterId)).hasSize(2);
    }

    @Test
    public void aNegativeClientVersionIsAConflictAndDecidesNothing() {
        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);
        statesDecidedOn.clear();

        assertThatThrownBy(() -> handler.handle(counterId, -1, new CounterCommand.Increment(10), this::decide))
                .isInstanceOf(ConcurrentModificationException.class);

        assertThat(statesDecidedOn).isEmpty();
        assertThat(handler.get(counterId).total()).isEqualTo(0);
    }

    @Test
    public void startingAStreamThatSomebodyElseStartedFirstIsRetriedOnTheirStream() {
        eventStore.competeBeforeTheNextAppends(1, new CounterEvent.Opened("views"));

        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);

        assertThat(handler.get(counterId).name()).isEqualTo("views");
        assertThat(eventsIn(counterId)).hasSize(1);
    }

    @Test
    public void readingAnEntityReturnsItsStateAfterAllEvents() {
        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);
        handler.handle(counterId, new CounterCommand.Increment(1), this::decide);
        handler.handle(counterId, new CounterCommand.Increment(2), this::decide);

        assertThat(handler.get(counterId)).isEqualTo(new Counter("clicks", 3));
    }

    @Test
    public void readingAnUnknownEntityReturnsItsInitialState() {
        assertThat(handler.get(UUID.randomUUID())).isEqualTo(Counter.initial());
    }

    @Test
    public void handlingACommandRecordsItsTypeStreamVersionsAndEventsOnASpan() {
        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);
        tracing.observations().clear();

        handler.handle(counterId, new CounterCommand.Increment(3), this::decide);

        assertThat(tracing.observations())
                .hasObservationWithNameEqualTo("eventdriven.command.handle")
                .that()
                .hasContextualNameEqualTo("command.handle")
                .hasLowCardinalityKeyValue("eventdriven.command.type", "Increment")
                .hasLowCardinalityKeyValue("eventdriven.command.status", "success")
                .hasHighCardinalityKeyValue("eventdriven.stream.name", mapToStreamId(counterId))
                .hasHighCardinalityKeyValue("eventdriven.stream.version.before", "0")
                .hasHighCardinalityKeyValue("eventdriven.stream.version.after", "1")
                .hasHighCardinalityKeyValue("eventdriven.command.event_count", "1")
                .hasHighCardinalityKeyValue("eventdriven.command.event_types", "Incremented");
    }

    @Test
    public void aCommandThatDecidesNothingRecordsNoEventsOnItsSpan() {
        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);
        tracing.observations().clear();

        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);

        assertThat(tracing.observations())
                .hasObservationWithNameEqualTo("eventdriven.command.handle")
                .that()
                .hasLowCardinalityKeyValue("eventdriven.command.status", "success")
                .hasHighCardinalityKeyValue("eventdriven.stream.version.before", "0")
                .hasHighCardinalityKeyValue("eventdriven.stream.version.after", "0")
                .hasHighCardinalityKeyValue("eventdriven.command.event_count", "0")
                .hasHighCardinalityKeyValue("eventdriven.command.event_types", "");
    }

    @Test
    public void aCommandOnANewStreamStartsItsSpanFromNoStream() {
        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);

        assertThat(tracing.observations())
                .hasObservationWithNameEqualTo("eventdriven.command.handle")
                .that()
                .hasHighCardinalityKeyValue("eventdriven.stream.version.before", "-1")
                .hasHighCardinalityKeyValue("eventdriven.stream.version.after", "0");
    }

    @Test
    public void handlingACommandCountsItAsHandledAndStartsItsFailuresAtZero() {
        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);

        assertThat(handledCount("Open", "success")).isEqualTo(1);
        assertThat(handledCount("Open", "failure")).isEqualTo(0);
    }

    @Test
    public void aFailedCommandCountsAsAFailure() {
        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);
        handler.handle(counterId, new CounterCommand.Increment(1), this::decide);

        assertThatThrownBy(() -> handler.handle(counterId, 0, new CounterCommand.Increment(10), this::decide))
                .isInstanceOf(ConcurrentModificationException.class);

        assertThat(handledCount("Increment", "success")).isEqualTo(1);
        assertThat(handledCount("Increment", "failure")).isEqualTo(1);
    }

    @Test
    public void aFailedCommandMarksItsSpanAsFailedWithTheException() {
        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);
        handler.handle(counterId, new CounterCommand.Increment(1), this::decide);
        tracing.observations().clear();

        assertThatThrownBy(() -> handler.handle(counterId, 0, new CounterCommand.Increment(10), this::decide))
                .isInstanceOf(ConcurrentModificationException.class);

        assertThat(tracing.observations())
                .hasObservationWithNameEqualTo("eventdriven.command.handle")
                .that()
                .hasLowCardinalityKeyValue("eventdriven.command.status", "failure")
                .assertThatError()
                .isInstanceOf(ConcurrentModificationException.class);
        var span = commandSpans().getLast();
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getEvents())
                .anySatisfy(event -> assertThat(event.getName()).isEqualTo("exception"));
    }

    @Test
    public void eachAttemptOfARetriedConflictHasItsOwnSpan() {
        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);
        eventStore.competeBeforeTheNextAppends(2);
        tracing.observations().clear();

        handler.handle(counterId, new CounterCommand.Increment(10), this::decide);

        assertThat(tracing.observations())
                .hasNumberOfObservationsWithNameEqualTo("eventdriven.command.handle", 3)
                .hasHandledContextsThatSatisfy(contexts -> assertThat(contexts.stream()
                                .filter(context -> context.getName().equals("eventdriven.command.handle"))
                                .map(context -> context.getLowCardinalityKeyValue("eventdriven.command.status")
                                        .getValue()))
                        .containsExactly("failure", "failure", "success"));
    }

    @Test
    public void aCommandHandledForAMessageCarriesItsCorrelationAndCausationIds() {
        try (var _ = messageContext.handling("message-1", "correlation-1")) {
            handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);
        }

        assertThat(tracing.observations())
                .hasObservationWithNameEqualTo("eventdriven.command.handle")
                .that()
                .hasHighCardinalityKeyValue("messaging.message.correlation_id", "correlation-1")
                .hasHighCardinalityKeyValue("messaging.message.causation_id", "message-1");
    }

    @Test
    public void aCommandContinuingATraceIsHandledInTheTraceOfTheStreamsLastEvent() {
        Span opening = tracing.tracer().nextSpan().name("opening").start();
        try (var _ = tracing.tracer().withSpan(opening);
                var _ = messageContext.handling("message-1", "correlation-1")) {
            handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);
        } finally {
            opening.end();
        }

        handler.handleContinuingTrace(counterId, new CounterCommand.Increment(1), this::decide);

        var span = commandSpans().getLast();
        assertThat(span.getTraceId()).isEqualTo(opening.context().traceId());
        assertThat(attributesOf(span))
                .containsEntry("eventdriven.command.type", "Increment")
                .containsEntry("messaging.message.correlation_id", "correlation-1");
    }

    @Test
    public void aCommandContinuingATraceForAMessageIsHandledBelowAStepNamingThatMessage() {
        handler.handle(counterId, new CounterCommand.Open("clicks"), this::decide);

        try (var _ = messageContext.handling("callback-1", "callback-correlation")) {
            handler.handleContinuingTrace(counterId, new CounterCommand.Increment(1), this::decide);
        }

        var step = tracing.finishedSpans().stream()
                .filter(span -> span.getName().equals("handle Increment"))
                .findFirst()
                .orElseThrow();
        assertThat(attributesOf(step)).containsEntry("messaging.message.id", "callback-1");
        assertThat(commandSpans().getLast().getParentSpanId()).isEqualTo(step.getSpanId());
    }

    private List<SpanData> commandSpans() {
        return tracing.finishedSpans().stream()
                .filter(span -> span.getName().equals("command.handle"))
                .toList();
    }

    private static Map<String, String> attributesOf(SpanData span) {
        var attributes = new HashMap<String, String>();
        span.getAttributes().forEach((key, value) -> attributes.put(key.getKey(), String.valueOf(value)));
        return attributes;
    }

    private CounterEvent[] decide(CounterCommand command, Counter state) {
        statesDecidedOn.add(state);

        return switch (command) {
            case CounterCommand.Open open ->
                state.name() != null ? new CounterEvent[0] : new CounterEvent[] {new CounterEvent.Opened(open.name())};
            case CounterCommand.Increment increment ->
                increment.by() == 0
                        ? new CounterEvent[0]
                        : new CounterEvent[] {new CounterEvent.Incremented(increment.by())};
        };
    }

    private List<Object> eventsIn(UUID id) {
        return switch (inMemoryEventStore.read(mapToStreamId(id))) {
            case EventStore.ReadResult.Success success ->
                success.events().stream().map(MessageEnvelope::message).toList();
            default -> List.of();
        };
    }

    private static String mapToStreamId(UUID id) {
        return "Counter-%s".formatted(id);
    }

    private double handledCount(String commandType, String status) {
        var counter = tracing.meters()
                .find("eventdriven.command.handling.count")
                .tags("eventdriven.command.type", commandType, "eventdriven.command.status", status)
                .counter();

        assertThat(counter)
                .as("Counter of %s with status %s", commandType, status)
                .isNotNull();
        return counter.count();
    }

    sealed interface CounterCommand {
        record Open(String name) implements CounterCommand {}

        record Increment(int by) implements CounterCommand {}
    }

    sealed interface CounterEvent {
        record Opened(String name) implements CounterEvent {}

        record Incremented(int by) implements CounterEvent {}
    }

    record Counter(String name, int total) {
        static Counter initial() {
            return new Counter(null, 0);
        }

        static Counter evolve(Counter state, CounterEvent event) {
            return switch (event) {
                case CounterEvent.Opened opened -> new Counter(opened.name(), state.total());
                case CounterEvent.Incremented incremented ->
                    new Counter(state.name(), state.total() + incremented.by());
            };
        }
    }

    static class CompetingWriterEventStore implements EventStore {
        private final EventStore store;
        private int competingAppends;
        private Object competingEvent = new CounterEvent.Incremented(1);

        CompetingWriterEventStore(EventStore store) {
            this.store = store;
        }

        void competeBeforeTheNextAppends(int count) {
            competingAppends = count;
        }

        void competeBeforeTheNextAppends(int count, Object event) {
            competingAppends = count;
            competingEvent = event;
        }

        @Override
        public ReadResult read(String streamId) {
            return store.read(streamId);
        }

        @Override
        public AppendResult append(String streamId, Object... events) {
            return store.append(streamId, events);
        }

        @Override
        public AppendResult append(String streamId, ExpectedStreamVersion expectedVersion, Object... events) {
            if (competingAppends > 0) {
                competingAppends--;
                store.append(streamId, ExpectedStreamVersion.any(), competingEvent);
            }

            return store.append(streamId, expectedVersion, events);
        }

        @Override
        public Subscription subscribe(String name, List<Class<?>> types, Consumer<MessageEnvelope<Object>> handler) {
            return store.subscribe(name, types, handler);
        }

        @Override
        public Subscription subscribe(String name, MessageProcessor... processors) {
            return store.subscribe(name, processors);
        }

        @Override
        public void whenCaughtUp(Duration timeout) {
            store.whenCaughtUp(timeout);
        }
    }
}
