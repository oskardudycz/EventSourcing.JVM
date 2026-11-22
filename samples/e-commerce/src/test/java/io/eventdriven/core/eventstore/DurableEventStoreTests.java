package io.eventdriven.core.eventstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.core.messaging.UnreadableMessageException;
import io.eventdriven.core.messaging.versioning.MessageMapping;
import io.eventdriven.core.messaging.versioning.MessageSerializer;
import io.eventdriven.core.projections.CounterEvent;
import io.eventdriven.core.projections.CounterId;
import io.eventdriven.core.projections.CounterProjection;
import io.eventdriven.core.projections.CounterRepository;
import io.eventdriven.core.subscriptions.SubscriptionCheckpoints;
import io.eventdriven.testing.messaging.TestMessages;
import io.eventdriven.testing.projections.ProjectionsDatabase;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class DurableEventStoreTests extends EventStoreTests {
    protected abstract EventStore newStore(ProjectionsDatabase projections, MessageSerializer serializer);

    protected abstract void close(EventStore store);

    protected abstract void appendRawEvent(String streamId, String typeName, String json);

    protected abstract List<String> storedTypeNamesOf(String streamId);

    protected EventStore newStore(ProjectionsDatabase projections) {
        return newStore(projections, serializer());
    }

    protected void storeCheckpointAfter(
            ProjectionsDatabase projections, String name, EventStore.AppendResult appended) {
        var checkpoint = ((EventStore.AppendResult.Success) appended).lastEvent();

        projections
                .bean(SubscriptionCheckpoints.class)
                .handle(name, checkpoint.transactionId(), checkpoint.position(), () -> {});
    }

    @Test
    public void readingAStreamWithAnUnknownTypeFailsNamingTheType() {
        var typeName = "unknown_type_%s".formatted(UUID.randomUUID().toString().replace("-", ""));
        appendRawEvent(streamId, typeName, "{}");

        assertThatThrownBy(() -> eventStore().read(streamId))
                .isInstanceOf(UnreadableMessageException.class)
                .hasMessageContaining(typeName);
    }

    @Test
    public void readingAStreamWithATrustedPackageButNoClassFailsNamingTheType() {
        var typeName = "%s.RemovedEvent".formatted(EventStoreTests.class.getPackageName());
        appendRawEvent(streamId, typeName, "{}");

        assertThatThrownBy(() -> eventStore().read(streamId))
                .isInstanceOf(UnreadableMessageException.class)
                .hasMessageContaining(typeName);
    }

    @Test
    public void aStoredEventCarriesItsTypeName() {
        eventStore().append(streamId, new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"));

        assertThat(storedTypeNamesOf(streamId)).containsExactly(CartOpened.class.getName(), ItemAdded.class.getName());
    }

    @Test
    public void aStoredEventWithAnOldNameIsReadAsTheCurrentClass() {
        var oldName = anOldNameOfItemAdded();
        var store = newStore(ProjectionsDatabase.H2, serializerWithOldNameOfItemAdded(oldName));
        appendRawEvent(streamId, oldName, "{\"cart\":\"%s\",\"item\":\"t-shirt\"}".formatted(cartId));

        var result = store.read(streamId);

        assertThat(result).isInstanceOf(EventStore.ReadResult.Success.class);
        assertThat(((EventStore.ReadResult.Success) result).events())
                .extracting(EventEnvelope::data)
                .containsExactly(new ItemAdded(cartId, "t-shirt"));
    }

    @Test
    public void aSubscriptionHandlesAnEventStoredUnderAnOldName() {
        var oldName = anOldNameOfItemAdded();
        var store = newStore(ProjectionsDatabase.H2, serializerWithOldNameOfItemAdded(oldName));
        var received = new CopyOnWriteArrayList<Object>();
        var subscription = store.subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().streamId().equals(streamId)) received.add(envelope.data());
        });
        appendRawEvent(streamId, oldName, "{\"cart\":\"%s\",\"item\":\"t-shirt\"}".formatted(cartId));

        var appended = store.append(streamId, ExpectedStreamVersion.any(), new ItemAdded(cartId, "hoodie"));

        subscription.awaitProcessed(((EventStore.AppendResult.Success) appended).lastEvent(), PROCESSING_TIMEOUT);
        assertThat(received).containsExactly(new ItemAdded(cartId, "t-shirt"), new ItemAdded(cartId, "hoodie"));
    }

    @Test
    public void aSubscriptionPassesOverAnEventWithAnUnknownName() {
        var received = new CopyOnWriteArrayList<Object>();
        var subscription = eventStore().subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().streamId().equals(streamId)) received.add(envelope.data());
        });
        appendRawEvent(
                streamId,
                "unknown_type_%s".formatted(UUID.randomUUID().toString().replace("-", "")),
                "{}");

        var appended = eventStore().append(streamId, ExpectedStreamVersion.any(), new ItemAdded(cartId, "t-shirt"));

        subscription.awaitProcessed(((EventStore.AppendResult.Success) appended).lastEvent(), PROCESSING_TIMEOUT);
        assertThat(received).containsExactly(new ItemAdded(cartId, "t-shirt"));
    }

    public record UnreadableOnPurpose(int count) {}

    @Test
    public void aSubscribedEventThatCantBeReadIsRetriedAndTheCheckpointStays() {
        var name = subscriptionName();
        var reads = new AtomicInteger();
        var countingSerializer =
                new MessageSerializer(serializer().typeMapper(), serializer().jsonMapper()) {
                    @Override
                    public Object read(String typeName, byte[] body) {
                        if (typeName.equals(UnreadableOnPurpose.class.getName())) reads.incrementAndGet();
                        return super.read(typeName, body);
                    }
                };
        var store = newStore(ProjectionsDatabase.H2, countingSerializer);
        var received = new CopyOnWriteArrayList<Object>();
        var readable = store.append(streamId, new ItemAdded(cartId, "t-shirt"));
        appendRawEvent(streamId, UnreadableOnPurpose.class.getName(), "{\"count\":\"not a number\"}");

        var subscription = store.subscribe(name, List.of(ItemAdded.class, UnreadableOnPurpose.class), envelope -> {
            if (envelope.metadata().streamId().equals(streamId)) received.add(envelope.data());
        });

        subscription.awaitProcessed(((EventStore.AppendResult.Success) readable).lastEvent(), PROCESSING_TIMEOUT);
        await().atMost(Duration.ofSeconds(30)).until(() -> reads.get() > 1);
        assertThat(received).containsExactly(new ItemAdded(cartId, "t-shirt"));
        assertThat(ProjectionsDatabase.H2.bean(SubscriptionCheckpoints.class).lastProcessed(name))
                .hasValue(((EventStore.AppendResult.Success) readable).lastEvent());
    }

    @Test
    public void failedEventIsDeliveredAgainAfterARestart() {
        var name = subscriptionName();
        var store = newStore(ProjectionsDatabase.H2);
        var failures = new AtomicInteger();
        store.subscribe(name, List.of(ItemAdded.class), envelope -> {
            if (!envelope.metadata().streamId().equals(streamId)) return;

            failures.incrementAndGet();
            throw new IllegalStateException("projection is down");
        });
        store.append(streamId, new ItemAdded(cartId, "t-shirt"));
        await().atMost(Duration.ofSeconds(10)).until(() -> failures.get() > 0);
        close(store);

        var restarted = newStore(ProjectionsDatabase.H2);
        var received = new CopyOnWriteArrayList<Object>();
        restarted.subscribe(name, List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().streamId().equals(streamId)) received.add(envelope.data());
        });

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(received).containsExactly(new ItemAdded(cartId, "t-shirt")));
    }

    @Test
    public void aSubscriptionThatFailsRepeatedlyGetsTheEventAgainUntilItSucceeds() {
        var store = newStore(ProjectionsDatabase.H2);
        var attempts = new AtomicInteger();
        var received = new CopyOnWriteArrayList<Object>();
        store.subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (!envelope.metadata().streamId().equals(streamId)) return;

            if (attempts.incrementAndGet() < 4) throw new IllegalStateException("projection is down");
            received.add(envelope.data());
        });

        store.append(streamId, new ItemAdded(cartId, "t-shirt"));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(received).containsExactly(new ItemAdded(cartId, "t-shirt")));
        assertThat(attempts).hasValue(4);
    }

    @Test
    public void closingAStoreReturnsOnlyAfterItsSubscriptionStopped() {
        var store = newStore(ProjectionsDatabase.H2);
        var attempts = new AtomicInteger();
        store.subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (!envelope.metadata().streamId().equals(streamId)) return;

            attempts.incrementAndGet();
            throw new IllegalStateException("projection is down");
        });
        store.append(streamId, new ItemAdded(cartId, "t-shirt"));
        await().atMost(Duration.ofSeconds(10)).until(() -> attempts.get() > 0);

        close(store);
        var attemptsAtClose = attempts.get();

        assertThat(attempts).hasValue(attemptsAtClose);
    }

    @Test
    public void closingAStoreWaitsForTheHandlerThatIsRunning() throws Exception {
        var store = newStore(ProjectionsDatabase.H2);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var handlerFinished = new AtomicBoolean(false);
        store.subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (!envelope.metadata().streamId().equals(streamId)) return;

            entered.countDown();
            awaitUninterruptibly(release);
            handlerFinished.set(true);
        });
        store.append(streamId, new ItemAdded(cartId, "t-shirt"));
        assertThat(entered.await(30, TimeUnit.SECONDS)).isTrue();
        var executor = Executors.newSingleThreadExecutor();

        try {
            var closing = executor.submit(() -> {
                close(store);
                return handlerFinished.get();
            });

            release.countDown();

            assertThat(closing.get(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        var interrupted = false;

        while (latch.getCount() > 0) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }

        if (interrupted) Thread.currentThread().interrupt();
    }

    @Test
    public void awaitingACheckpointHandledBeforeARestartReturnsAfterTheRestart() {
        var name = subscriptionName();
        var store = newStore(ProjectionsDatabase.H2);
        var subscription = store.subscribe(name, List.of(ItemAdded.class), envelope -> {});
        var appended = store.append(streamId, new ItemAdded(cartId, "t-shirt"));
        var checkpoint = ((EventStore.AppendResult.Success) appended).lastEvent();
        subscription.awaitProcessed(checkpoint, PROCESSING_TIMEOUT);
        close(store);

        var restarted = newStore(ProjectionsDatabase.H2);
        var restartedSubscription = restarted.subscribe(name, List.of(ItemAdded.class), envelope -> {});

        assertThatNoException().isThrownBy(() -> restartedSubscription.awaitProcessed(checkpoint, PROCESSING_TIMEOUT));
    }

    @Test
    public void subscriptionContinuesAfterItsCheckpointOnRestart() {
        var name = subscriptionName();
        var store = newStore(ProjectionsDatabase.POSTGRESQL);
        var forwarded = new CopyOnWriteArrayList<Object>();
        var subscription = store.subscribe(name, List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().streamId().equals(streamId)) forwarded.add(envelope.data());
        });
        store.append(
                streamId,
                new ItemAdded(cartId, "t-shirt"),
                new ItemAdded(cartId, "hoodie"),
                new ItemAdded(cartId, "socks"));
        var result = store.append(streamId, ExpectedStreamVersion.any(), new ItemAdded(cartId, "scarf"));
        subscription.awaitProcessed(((EventStore.AppendResult.Success) result).lastEvent(), PROCESSING_TIMEOUT);
        close(store);
        forwarded.clear();

        var restarted = newStore(ProjectionsDatabase.POSTGRESQL);
        var restartedSubscription = restarted.subscribe(name, List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().streamId().equals(streamId)) forwarded.add(envelope.data());
        });
        var gloves = restarted.append(streamId, ExpectedStreamVersion.any(), new ItemAdded(cartId, "gloves"));

        restartedSubscription.awaitProcessed(
                ((EventStore.AppendResult.Success) gloves).lastEvent(), PROCESSING_TIMEOUT);
        assertThat(forwarded).containsExactly(new ItemAdded(cartId, "gloves"));
    }

    @Test
    public void failedCheckpointRollsBackTheProjectionsDocument() {
        var name = subscriptionName();
        var repository = ProjectionsDatabase.POSTGRESQL.bean(CounterRepository.class);
        var jdbc = new JdbcTemplate(ProjectionsDatabase.POSTGRESQL.bean(DataSource.class));
        var store = newStore(ProjectionsDatabase.POSTGRESQL);
        var counters = new CounterProjection(repository);
        var counterId = aCounterId();
        var attempts = new AtomicInteger();
        var earlierCounterId = aCounterId();
        var earlier = store.append(streamOf(earlierCounterId), new CounterEvent.Started(earlierCounterId));
        storeCheckpointAfter(ProjectionsDatabase.POSTGRESQL, name, earlier);
        store.append(streamOf(counterId), new CounterEvent.Started(counterId));
        failCheckpointStoreOf(jdbc, name);

        try {
            store.subscribe(name, counters.canHandle(), envelope -> {
                if (!isCounterOf(envelope, counterId)) return;

                counters.handle(envelope);
                attempts.incrementAndGet();
            });

            await().atMost(Duration.ofSeconds(10)).until(() -> attempts.get() >= 2);
            assertThat(repository.findById(counterId)).isEmpty();
        } finally {
            repairCheckpointStoreOf(jdbc, name);
        }

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(repository.findById(counterId)).isPresent());
    }

    @Test
    public void eventAlreadyPastTheCheckpointIsSkippedAndRolledBack() {
        var name = subscriptionName();
        var store = newStore(ProjectionsDatabase.POSTGRESQL);
        var repository = ProjectionsDatabase.POSTGRESQL.bean(CounterRepository.class);
        var checkpoints = ProjectionsDatabase.POSTGRESQL.bean(SubscriptionCheckpoints.class);
        var counters = new CounterProjection(repository);
        var warmUpCounterId = aCounterId();
        var counterId = aCounterId();
        var subscription = store.subscribe(name, counters.canHandle(), envelope -> {
            if (isCounterOf(envelope, warmUpCounterId) || isCounterOf(envelope, counterId)) counters.handle(envelope);
        });
        var warmUp = store.append(streamOf(warmUpCounterId), new CounterEvent.Started(warmUpCounterId));
        subscription.awaitProcessed(((EventStore.AppendResult.Success) warmUp).lastEvent(), PROCESSING_TIMEOUT);
        checkpoints.handle(name, Long.MAX_VALUE, Long.MAX_VALUE, () -> {});

        var appended = store.append(streamOf(counterId), new CounterEvent.Started(counterId));

        subscription.awaitProcessed(((EventStore.AppendResult.Success) appended).lastEvent(), PROCESSING_TIMEOUT);
        assertThat(repository.findById(counterId)).isEmpty();
        assertThat(checkpoints.lastProcessedPosition(name)).hasValue(Long.MAX_VALUE);
    }

    private static String anOldNameOfItemAdded() {
        return "old.ItemAdded.%s".formatted(UUID.randomUUID().toString().replace("-", ""));
    }

    private static MessageSerializer serializerWithOldNameOfItemAdded(String oldName) {
        var oldNameMapping = new MessageMapping()
                .register(
                        oldName,
                        ItemAdded.class,
                        json -> new ItemAdded(
                                json.get("cart").asString(), json.get("item").asString()));

        return TestMessages.serializer(
                Stream.concat(mappings().stream(), Stream.of(oldNameMapping)).toList());
    }

    private static CounterId aCounterId() {
        return new CounterId(UUID.randomUUID().toString());
    }

    private static String streamOf(CounterId counterId) {
        return "Counter-%s".formatted(counterId.value());
    }

    private static boolean isCounterOf(EventEnvelope<?> envelope, CounterId counterId) {
        return envelope.metadata().streamId().equals(streamOf(counterId));
    }

    private static void failCheckpointStoreOf(JdbcTemplate jdbc, String processorId) {
        var function = functionOf(processorId);

        jdbc.execute(
                "create function %s() returns trigger as $$ begin raise exception 'checkpoint store is down'; end $$ language plpgsql"
                        .formatted(function));
        jdbc.execute(
                "create trigger %s before insert or update on processor_checkpoints for each row when (new.processor_id = '%s') execute function %s()"
                        .formatted(function, processorId, function));
    }

    private static void repairCheckpointStoreOf(JdbcTemplate jdbc, String processorId) {
        var function = functionOf(processorId);

        jdbc.execute("drop trigger %s on processor_checkpoints".formatted(function));
        jdbc.execute("drop function %s()".formatted(function));
    }

    private static String functionOf(String processorId) {
        return "fail_%s".formatted(processorId.replace("-", "_"));
    }
}
