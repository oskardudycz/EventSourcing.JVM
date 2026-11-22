package io.eventdriven.core.eventstore.kurrentdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.core.eventstore.DurableEventStoreTests;
import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.core.messaging.versioning.MessageSerializer;
import io.eventdriven.core.processing.RetryBackOff;
import io.eventdriven.core.projections.Counter;
import io.eventdriven.core.projections.CounterEvent;
import io.eventdriven.core.projections.CounterId;
import io.eventdriven.core.projections.CounterProjection;
import io.eventdriven.core.projections.CounterRepository;
import io.eventdriven.core.subscriptions.SubscriptionCheckpoints;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.projections.ProjectionsDatabase;
import io.eventdriven.testing.tracing.TestTracing;
import io.kurrent.dbclient.AppendToStreamOptions;
import io.kurrent.dbclient.EventDataBuilder;
import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.KurrentDBConnectionString;
import io.kurrent.dbclient.ReadStreamOptions;
import io.kurrent.dbclient.StreamState;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class KurrentDBEventStoreTests extends DurableEventStoreTests {
    private final KurrentDBClient client =
            KurrentDBClient.create(KurrentDBConnectionString.parseOrThrow(TestDatabases.kurrentDBConnectionString()));
    private final List<KurrentDBEventStore> stores = new ArrayList<>();
    private final KurrentDBEventStore eventStore = (KurrentDBEventStore) newStore(ProjectionsDatabase.H2);

    @Override
    protected EventStore eventStore() {
        return eventStore;
    }

    @Override
    protected EventStore newStore(ProjectionsDatabase projections, MessageSerializer serializer) {
        var store = new KurrentDBEventStore(
                client,
                TestTracing.untraced(),
                serializer,
                ObservationRegistry.NOOP,
                projections.bean(SubscriptionCheckpoints.class),
                RetryBackOff.exponential(Duration.ofMillis(100)));
        stores.add(store);

        return store;
    }

    @Override
    protected void close(EventStore store) {
        ((KurrentDBEventStore) store).close();
    }

    @Override
    protected void appendRawEvent(String streamId, String typeName, String json) {
        try {
            client.appendToStream(
                            streamId,
                            AppendToStreamOptions.get().streamState(StreamState.any()),
                            EventDataBuilder.json(typeName, json.getBytes()).build())
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    protected List<String> storedTypeNamesOf(String streamId) {
        try {
            return client.readStream(streamId, ReadStreamOptions.get()).get().getEvents().stream()
                    .map(event -> event.getEvent().getEventType())
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterEach
    void closeStores() {
        stores.forEach(KurrentDBEventStore::close);
        client.shutdown();
    }

    @Test
    public void aCancelledSubscriptionSubscribesAgainFromItsCheckpoint() {
        var name = subscriptionName();
        var store = newStore(ProjectionsDatabase.POSTGRESQL);
        var checkpoints = ProjectionsDatabase.POSTGRESQL.bean(SubscriptionCheckpoints.class);
        var earlier = store.append("ShoppingCart-%s".formatted(UUID.randomUUID()), new CartOpened(cartId));
        var appended = store.append(streamId, new ItemAdded(cartId, "t-shirt"));
        var received = new CopyOnWriteArrayList<Object>();
        checkpoints.handle(name, 1, () -> {});
        var subscription = store.subscribe(name, List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().streamId().equals(streamId)) received.add(envelope.data());
        });

        checkpoints.handle(
                name, ((EventStore.AppendResult.Success) earlier).lastEvent().position(), () -> {});

        subscription.whenProcessed(((EventStore.AppendResult.Success) appended).lastEvent());
        assertThat(received).containsExactly(new ItemAdded(cartId, "t-shirt"));
    }

    @Test
    public void closingAStoreThatOwnsItsClientShutsTheClientDown() {
        var store = KurrentDBEventStore.connectingTo(
                TestDatabases.kurrentDBConnectionString(),
                TestTracing.untraced(),
                serializer(),
                ObservationRegistry.NOOP,
                ProjectionsDatabase.H2.bean(SubscriptionCheckpoints.class),
                RetryBackOff.exponential(Duration.ofMillis(100)));
        assertThat(store.append(streamId, new CartOpened(cartId)).succeeded()).isTrue();

        store.close();

        assertThat(store.append(streamId, ExpectedStreamVersion.any(), new ItemAdded(cartId, "t-shirt")))
                .isInstanceOf(EventStore.AppendResult.UnexpectedFailure.class);
    }

    @Test
    public void aProjectionGetsItsEventsInStreamOrderOnKurrentDB() {
        var repository = ProjectionsDatabase.H2.bean(CounterRepository.class);
        var counters = new CounterProjection(repository);
        eventStore.subscribe(subscriptionName(), counters.canHandle(), counters::handle);
        var counterId = new CounterId(UUID.randomUUID().toString());

        eventStore.append(
                "Counter-%s".formatted(counterId.value()),
                new CounterEvent.Started(counterId),
                new CounterEvent.Incremented(counterId));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var counter = assertThat(repository.findById(counterId)).get();

            counter.extracting(Counter::getVersion).isEqualTo(1L);
            counter.extracting(Counter::getCount).isEqualTo(1);
        });
    }
}
