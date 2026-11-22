package io.eventdriven.core.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.core.eventstore.Subscription;
import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.testing.messaging.TestMessages;
import io.eventdriven.testing.tracing.TestTracing;
import java.time.Duration;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class CommandHandlerTests {
    private final InMemoryEventStore inMemoryEventStore = new InMemoryEventStore(
            TestTracing.untraced(), TestMessages.serializerTrustingPackageOf(CommandHandlerTests.class));
    private final CompetingWriterEventStore eventStore = new CompetingWriterEventStore(inMemoryEventStore);
    private final List<Counter> statesDecidedOn = new ArrayList<>();
    private final CommandHandler<UUID, Counter, CounterEvent> handler = new CommandHandler<>(
            eventStore, TestTracing.untraced(), CommandHandlerTests::mapToStreamId, Counter::initial, Counter::evolve);

    private final UUID counterId = UUID.randomUUID();

    @Test
    public void handlingACommandFoldsTheStreamAndAppendsWithTheVersionRead() {
        var afterOpen = handler.handle(counterId, state -> decide(new CounterCommand.Open("clicks"), state));

        var afterIncrement = handler.handle(counterId, state -> decide(new CounterCommand.Increment(3), state));

        assertThat(afterOpen).isEqualTo(0);
        assertThat(afterIncrement).isEqualTo(1);
        assertThat(statesDecidedOn).containsExactly(Counter.initial(), new Counter("clicks", 0));
        assertThat(eventsIn(counterId))
                .containsExactly(new CounterEvent.Opened("clicks"), new CounterEvent.Incremented(3));
    }

    @Test
    public void aCommandThatDecidesNothingAppendsNothing() {
        handler.handle(counterId, state -> decide(new CounterCommand.Open("clicks"), state));

        var version = handler.handle(counterId, state -> decide(new CounterCommand.Open("clicks"), state));

        assertThat(version).isEqualTo(0);
        assertThat(eventsIn(counterId)).hasSize(1);
    }

    @Test
    public void aCommandOnAnUnknownStreamThatDecidesNothingReportsNoStream() {
        var version = handler.handle(counterId, state -> decide(new CounterCommand.Increment(0), state));

        assertThat(version).isEqualTo(-1);
        assertThat(eventsIn(counterId)).isEmpty();
    }

    @Test
    public void aConflictWithoutAClientVersionIsRetriedUpToThreeAttempts() {
        handler.handle(counterId, state -> decide(new CounterCommand.Open("clicks"), state));
        statesDecidedOn.clear();
        eventStore.competeBeforeTheNextAppends(2);

        var version = handler.handle(counterId, state -> decide(new CounterCommand.Increment(10), state));

        assertThat(statesDecidedOn).hasSize(3);
        assertThat(version).isEqualTo(3);
        assertThat(handler.get(counterId).total()).isEqualTo(12);
    }

    @Test
    public void aThirdConflictWithoutAClientVersionFailsTheCommand() {
        handler.handle(counterId, state -> decide(new CounterCommand.Open("clicks"), state));
        statesDecidedOn.clear();
        eventStore.competeBeforeTheNextAppends(3);

        assertThatThrownBy(() -> handler.handle(counterId, state -> decide(new CounterCommand.Increment(10), state)))
                .isInstanceOf(ConcurrentModificationException.class)
                .hasMessageContaining(mapToStreamId(counterId));

        assertThat(statesDecidedOn).hasSize(3);
        assertThat(handler.get(counterId).total()).isEqualTo(3);
    }

    @Test
    public void aConflictWithAClientVersionIsNotRetried() {
        handler.handle(counterId, state -> decide(new CounterCommand.Open("clicks"), state));
        handler.handle(counterId, state -> decide(new CounterCommand.Increment(1), state));
        statesDecidedOn.clear();

        assertThatThrownBy(() -> handler.handle(counterId, 0, state -> decide(new CounterCommand.Increment(10), state)))
                .isInstanceOf(ConcurrentModificationException.class);

        assertThat(statesDecidedOn).hasSize(1);
        assertThat(handler.get(counterId).total()).isEqualTo(1);
    }

    @Test
    public void aCommandWithTheCurrentClientVersionIsAppendedAndReturnsTheNextVersion() {
        handler.handle(counterId, state -> decide(new CounterCommand.Open("clicks"), state));

        var version = handler.handle(counterId, 0, state -> decide(new CounterCommand.Increment(10), state));

        assertThat(version).isEqualTo(1);
        assertThat(handler.get(counterId).total()).isEqualTo(10);
    }

    @Test
    public void aCommandWithAClientVersionThatDecidesNothingReturnsThatVersion() {
        handler.handle(counterId, state -> decide(new CounterCommand.Open("clicks"), state));
        handler.handle(counterId, state -> decide(new CounterCommand.Increment(1), state));

        var version = handler.handle(counterId, 1, state -> decide(new CounterCommand.Increment(0), state));

        assertThat(version).isEqualTo(1);
        assertThat(eventsIn(counterId)).hasSize(2);
    }

    @Test
    public void aNegativeClientVersionIsAConflictAndDecidesNothing() {
        handler.handle(counterId, state -> decide(new CounterCommand.Open("clicks"), state));
        statesDecidedOn.clear();

        assertThatThrownBy(
                        () -> handler.handle(counterId, -1, state -> decide(new CounterCommand.Increment(10), state)))
                .isInstanceOf(ConcurrentModificationException.class);

        assertThat(statesDecidedOn).isEmpty();
        assertThat(handler.get(counterId).total()).isEqualTo(0);
    }

    @Test
    public void startingAStreamThatSomebodyElseStartedFirstIsRetriedOnTheirStream() {
        eventStore.competeBeforeTheNextAppends(1, new CounterEvent.Opened("views"));

        handler.handle(counterId, state -> decide(new CounterCommand.Open("clicks"), state));

        assertThat(handler.get(counterId).name()).isEqualTo("views");
        assertThat(eventsIn(counterId)).hasSize(1);
    }

    @Test
    public void readingAnEntityReturnsItsStateAfterAllEvents() {
        handler.handle(counterId, state -> decide(new CounterCommand.Open("clicks"), state));
        handler.handle(counterId, state -> decide(new CounterCommand.Increment(1), state));
        handler.handle(counterId, state -> decide(new CounterCommand.Increment(2), state));

        assertThat(handler.get(counterId)).isEqualTo(new Counter("clicks", 3));
    }

    @Test
    public void readingAnUnknownEntityReturnsItsInitialState() {
        assertThat(handler.get(UUID.randomUUID())).isEqualTo(Counter.initial());
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
                success.events().stream().map(EventEnvelope::data).toList();
            default -> List.of();
        };
    }

    private static String mapToStreamId(UUID id) {
        return "Counter-%s".formatted(id);
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
        public Subscription subscribe(String name, List<Class<?>> types, Consumer<EventEnvelope<Object>> handler) {
            return store.subscribe(name, types, handler);
        }

        @Override
        public void whenCaughtUp(Duration timeout) {
            store.whenCaughtUp(timeout);
        }
    }
}
