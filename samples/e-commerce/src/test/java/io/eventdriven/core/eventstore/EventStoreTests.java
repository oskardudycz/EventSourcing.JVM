package io.eventdriven.core.eventstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import io.eventdriven.core.eventstore.EventStore.AppendResult;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.messaging.MessageMetadata;
import io.eventdriven.core.messaging.versioning.MessageMapping;
import io.eventdriven.core.messaging.versioning.MessageSerializer;
import io.eventdriven.core.processing.MessageProcessor;
import io.eventdriven.core.projections.CounterEvent;
import io.eventdriven.testing.messaging.TestMessages;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public abstract class EventStoreTests {

    protected final String cartId = UUID.randomUUID().toString();
    protected final String streamId = "ShoppingCart-%s".formatted(cartId);

    protected abstract EventStore eventStore();

    protected static List<MessageMapping> mappings() {
        return List.of(TestMessages.trustingPackageOf(EventStoreTests.class, CounterEvent.class));
    }

    protected static MessageSerializer serializer() {
        return TestMessages.serializer(mappings());
    }

    @Test
    public void appendedEventsComeBackInOrder() {
        var opened = new CartOpened(cartId);
        var added = new ItemAdded(cartId, "t-shirt");
        var third = new ItemAdded(cartId, "hoodie");

        eventStore().append(streamId, opened, added, third);

        assertThat(eventsOf(streamId)).containsExactly(opened, added, third);
    }

    @Test
    public void anOffsetDateTimeKeepsItsOffset() {
        var openedAt = OffsetDateTime.of(2024, 5, 1, 10, 15, 30, 0, ZoneOffset.ofHours(2));

        eventStore().append(streamId, new CartOpenedAt(cartId, openedAt));

        assertThat(eventsOf(streamId)).containsExactly(new CartOpenedAt(cartId, openedAt));
        assertThat(((CartOpenedAt) eventsOf(streamId).getFirst()).openedAt().getOffset())
                .isEqualTo(ZoneOffset.ofHours(2));
    }

    @Test
    public void appendingTwoEventsToANewStreamReturnsVersionOneAndTheyReadBackAtZeroAndOne() {
        var opened = new CartOpened(cartId);
        var added = new ItemAdded(cartId, "t-shirt");

        var result = eventStore().append(streamId, ExpectedStreamVersion.noStream(), opened, added);

        var metadata = metadataOf(streamId);
        assertThat(result).isInstanceOf(AppendResult.Success.class);
        assertThat(((AppendResult.Success) result).nextStreamVersion()).isEqualTo(1);
        assertThat(((AppendResult.Success) result).lastEvent().position())
                .isEqualTo(metadata.getLast().stream().logPosition());
        assertThat(metadata).extracting(each -> each.stream().streamPosition()).containsExactly(0L, 1L);
        assertThat(eventsOf(streamId)).containsExactly(opened, added);
    }

    @Test
    public void appendingExpectingNoStreamToAnExistingStreamSaysItAlreadyExistsAndWritesNothing() {
        eventStore().append(streamId, new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"));

        var result = eventStore().append(streamId, ExpectedStreamVersion.noStream(), new ItemAdded(cartId, "hoodie"));

        assertThat(result).isEqualTo(new AppendResult.StreamAlreadyExists(1));
        assertThat(eventsOf(streamId)).containsExactly(new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"));
    }

    @Test
    public void appendingWithAStaleVersionIsAConflictWithBothVersionsAndWritesNothing() {
        eventStore().append(streamId, new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"));

        var result = eventStore().append(streamId, ExpectedStreamVersion.at(0), new ItemAdded(cartId, "hoodie"));

        assertThat(result).isEqualTo(new AppendResult.Conflict(ExpectedStreamVersion.at(0), 1));
        assertThat(eventsOf(streamId)).containsExactly(new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"));
    }

    @Test
    public void appendingExpectingAnyVersionWritesAfterTheLastEvent() {
        eventStore().append(streamId, new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"));

        var result = eventStore()
                .append(
                        streamId,
                        ExpectedStreamVersion.any(),
                        new ItemAdded(cartId, "hoodie"),
                        new ItemAdded(cartId, "socks"));

        assertThat(((AppendResult.Success) result).nextStreamVersion()).isEqualTo(3);
        assertThat(metadataOf(streamId))
                .extracting(each -> each.stream().streamPosition())
                .containsExactly(0L, 1L, 2L, 3L);
    }

    @Test
    public void appendingExpectingTheStreamExistsWritesAfterTheLastEvent() {
        eventStore().append(streamId, new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"));

        var result = eventStore()
                .append(
                        streamId,
                        ExpectedStreamVersion.streamExists(),
                        new ItemAdded(cartId, "hoodie"),
                        new ItemAdded(cartId, "socks"));

        assertThat(((AppendResult.Success) result).nextStreamVersion()).isEqualTo(3);
        assertThat(metadataOf(streamId))
                .extracting(each -> each.stream().streamPosition())
                .containsExactly(0L, 1L, 2L, 3L);
    }

    @Test
    public void appendingExpectingTheStreamExistsToNoStreamIsAConflict() {
        var result =
                eventStore().append(streamId, ExpectedStreamVersion.streamExists(), new ItemAdded(cartId, "t-shirt"));

        assertThat(result).isEqualTo(new AppendResult.Conflict(ExpectedStreamVersion.streamExists(), -1));
        assertThat(eventStore().read(streamId)).isInstanceOf(EventStore.ReadResult.StreamDoesNotExist.class);
    }

    @Test
    public void appendingExpectingAnyVersionToANewStreamStartsItAtVersionZero() {
        var result = eventStore().append(streamId, ExpectedStreamVersion.any(), new CartOpened(cartId));

        assertThat(((AppendResult.Success) result).nextStreamVersion()).isEqualTo(0);
        assertThat(eventsOf(streamId)).containsExactly(new CartOpened(cartId));
    }

    @Test
    public void appendingExpectingAVersionToNoStreamIsAConflictAndWritesNothing() {
        var result = eventStore().append(streamId, ExpectedStreamVersion.at(0), new ItemAdded(cartId, "t-shirt"));

        assertThat(result).isEqualTo(new AppendResult.Conflict(ExpectedStreamVersion.at(0), -1));
        assertThat(eventStore().read(streamId)).isInstanceOf(EventStore.ReadResult.StreamDoesNotExist.class);
    }

    @Test
    public void appendingNoEventsIsRefusedAndCreatesNoStream() {
        assertThatThrownBy(() -> eventStore().append(streamId, ExpectedStreamVersion.noStream()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(eventStore().read(streamId)).isInstanceOf(EventStore.ReadResult.StreamDoesNotExist.class);
    }

    @Test
    public void readingAnUnknownStreamSaysItDoesNotExist() {
        assertThat(eventStore().read(streamId)).isInstanceOf(EventStore.ReadResult.StreamDoesNotExist.class);
    }

    @Test
    public void appendingThreeEventsReturnsTheCheckpointOfTheThird() {
        var result = eventStore()
                .append(
                        streamId,
                        new CartOpened(cartId),
                        new ItemAdded(cartId, "t-shirt"),
                        new ItemAdded(cartId, "hoodie"));

        assertThat(((AppendResult.Success) result).lastEvent())
                .isEqualTo(metadataOf(streamId).getLast().stream().checkpoint());
    }

    @Test
    public void concurrentAppendsWithTheSameVersionLetExactlyOneWin() throws Exception {
        var executor = Executors.newFixedThreadPool(2);

        try {
            for (var round = 0; round < 20; round++) {
                var roundStreamId = "ShoppingCart-%s".formatted(UUID.randomUUID());
                eventStore().append(roundStreamId, new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"));
                var release = new CountDownLatch(1);

                var first = executor.submit(() -> {
                    release.await();
                    return eventStore()
                            .append(roundStreamId, ExpectedStreamVersion.at(1), new ItemAdded(cartId, "hoodie"));
                });
                var second = executor.submit(() -> {
                    release.await();
                    return eventStore()
                            .append(roundStreamId, ExpectedStreamVersion.at(1), new ItemAdded(cartId, "socks"));
                });
                release.countDown();

                assertThat(List.of(first.get(), second.get()))
                        .filteredOn(AppendResult::succeeded)
                        .hasSize(1);
                assertThat(List.of(first.get(), second.get()))
                        .filteredOn(result -> result instanceof AppendResult.Conflict)
                        .hasSize(1);
                assertThat(eventsOf(roundStreamId))
                        .hasSize(3)
                        .containsAnyOf(new ItemAdded(cartId, "hoodie"), new ItemAdded(cartId, "socks"));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void aSubscriptionNotifiesByTheExactClassOfTheEventNotByItsSupertype() {
        var received = new CopyOnWriteArrayList<Object>();
        eventStore().subscribe(subscriptionName(), List.of(CartEvent.class), envelope -> {
            if (envelope.metadata().stream().streamId().equals(streamId)) received.add(envelope.message());
        });
        var marker = eventStore().subscribe(subscriptionName(), List.of(CartOpened.class), envelope -> {});

        var result = eventStore().append(streamId, new CartOpened(cartId));

        marker.whenProcessed(((AppendResult.Success) result).lastEvent());
        assertThat(received).isEmpty();
    }

    @Test
    public void aFailedAppendNotifiesNobody() {
        eventStore().append(streamId, new CartOpened(cartId));
        var received = new CopyOnWriteArrayList<Object>();
        var subscription = eventStore().subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().stream().streamId().equals(streamId)) received.add(envelope.message());
        });

        var failed = eventStore().append(streamId, ExpectedStreamVersion.at(4), new ItemAdded(cartId, "t-shirt"));
        var next = eventStore().append(streamId, ExpectedStreamVersion.any(), new ItemAdded(cartId, "hoodie"));

        assertThat(failed.succeeded()).isFalse();
        subscription.whenProcessed(((AppendResult.Success) next).lastEvent());
        assertThat(received).containsExactly(new ItemAdded(cartId, "hoodie"));
    }

    @Test
    public void awaitingAnAppendedCheckpointReturnsOnceTheSubscriberHandledIt() {
        var received = new CopyOnWriteArrayList<Object>();
        var subscription = eventStore().subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().stream().streamId().equals(streamId)) received.add(envelope.message());
        });

        var result = eventStore().append(streamId, new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"));

        subscription.whenProcessed(((AppendResult.Success) result).lastEvent());
        assertThat(received).containsExactly(new ItemAdded(cartId, "t-shirt"));
    }

    @Test
    public void awaitingAnEarlierCheckpointReturnsOnceALaterEventIsHandled() {
        var subscription = eventStore().subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {});
        var earlier = eventStore().append(streamId, new ItemAdded(cartId, "t-shirt"));
        var later = eventStore().append(streamId, ExpectedStreamVersion.any(), new ItemAdded(cartId, "hoodie"));
        subscription.whenProcessed(((AppendResult.Success) later).lastEvent());

        assertThatNoException()
                .isThrownBy(() ->
                        subscription.whenProcessed(((AppendResult.Success) earlier).lastEvent(), Duration.ofMillis(1)));
    }

    @Test
    public void awaitingACheckpointFailsAfterTheTimeoutWhenTheSubscriberFails() {
        var name = subscriptionName();
        var subscription = eventStore().subscribe(name, List.of(ItemAdded.class), envelope -> {
            throw new IllegalStateException("projection is down");
        });
        var result = eventStore().append(streamId, new ItemAdded(cartId, "t-shirt"));

        assertThatThrownBy(() ->
                        subscription.whenProcessed(((AppendResult.Success) result).lastEvent(), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(name);
    }

    @Test
    public void awaitingCatchUpReturnsOnceEverySubscriptionHandledWhatWasAppended() {
        var openedCarts = new CopyOnWriteArrayList<Object>();
        var addedItems = new CopyOnWriteArrayList<Object>();
        eventStore().subscribe(subscriptionName(), List.of(CartOpened.class), envelope -> {
            if (envelope.metadata().stream().streamId().equals(streamId)) openedCarts.add(envelope.message());
        });
        eventStore().subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().stream().streamId().equals(streamId)) addedItems.add(envelope.message());
        });

        eventStore().append(streamId, new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"));

        eventStore().whenCaughtUp();
        assertThat(openedCarts).containsExactly(new CartOpened(cartId));
        assertThat(addedItems).containsExactly(new ItemAdded(cartId, "t-shirt"));
    }

    @Test
    public void aSubscriptionRunsInOrderEachProcessorThatCanHandleTheEvent() {
        var handled = new CopyOnWriteArrayList<String>();
        eventStore()
                .subscribe(
                        subscriptionName(),
                        processor("opened carts", handled, CartOpened.class),
                        processor("cart items", handled, CartOpened.class, ItemAdded.class));

        eventStore().append(streamId, new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"));

        eventStore().whenCaughtUp();
        assertThat(handled)
                .containsExactly("opened carts: CartOpened", "cart items: CartOpened", "cart items: ItemAdded");
    }

    private MessageProcessor processor(String name, List<String> handled, Class<?>... types) {
        return new MessageProcessor() {
            @Override
            public List<Class<?>> canHandle() {
                return List.of(types);
            }

            @Override
            public void handle(MessageEnvelope<Object> envelope) {
                if (envelope.metadata().stream().streamId().equals(streamId))
                    handled.add(name + ": " + envelope.message().getClass().getSimpleName());
            }
        };
    }

    @Test
    public void awaitingCatchUpAlsoWaitsForWhatSubscriptionsAppendedWhileItWaited() {
        var itemsStreamId = streamId + "-items";
        var addedItems = new CopyOnWriteArrayList<Object>();
        eventStore().subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().stream().streamId().equals(itemsStreamId)) addedItems.add(envelope.message());
        });
        eventStore().subscribe(subscriptionName(), List.of(CartOpened.class), envelope -> {
            if (envelope.metadata().stream().streamId().equals(streamId))
                eventStore().append(itemsStreamId, new ItemAdded(cartId, "t-shirt"));
        });

        eventStore().append(streamId, new CartOpened(cartId));

        eventStore().whenCaughtUp();
        assertThat(addedItems).containsExactly(new ItemAdded(cartId, "t-shirt"));
    }

    @Test
    public void awaitingCatchUpFailsNamingTheSubscriptionThatDidNotHandleItsEventsInTime() {
        var name = subscriptionName();
        eventStore().subscribe(name, List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().stream().streamId().equals(streamId))
                throw new IllegalStateException("Handler is down");
        });

        eventStore().append(streamId, new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"));

        assertThatThrownBy(() -> eventStore().whenCaughtUp(Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(name);
    }

    @Test
    public void awaitingCatchUpDoesNotWaitForASubscriptionWhoseTypesWereNotAppended() {
        eventStore().subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().stream().streamId().equals(streamId))
                throw new IllegalStateException("Handler is down");
        });

        eventStore().append(streamId, new CartOpened(cartId));

        eventStore().whenCaughtUp(Duration.ofSeconds(5));
    }

    @Test
    public void failedEventIsDeliveredAgainBeforeNewerEvents() {
        var attempts = new AtomicInteger();
        var received = new CopyOnWriteArrayList<Object>();
        eventStore().subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (!envelope.metadata().stream().streamId().equals(streamId)) return;

            received.add(envelope.message());
            if (attempts.getAndIncrement() == 0) throw new IllegalStateException("projection is down");
        });

        eventStore().append(streamId, new ItemAdded(cartId, "t-shirt"));
        eventStore().append(streamId, ExpectedStreamVersion.any(), new ItemAdded(cartId, "hoodie"));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(received)
                        .containsExactly(
                                new ItemAdded(cartId, "t-shirt"),
                                new ItemAdded(cartId, "t-shirt"),
                                new ItemAdded(cartId, "hoodie")));
    }

    @Test
    public void newSubscriberGetsTheExistingEventsFromTheStart() {
        eventStore().append(streamId, new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"));
        var received = new CopyOnWriteArrayList<Object>();

        eventStore().subscribe(subscriptionName(), List.of(CartOpened.class, ItemAdded.class), envelope -> {
            if (envelope.metadata().stream().streamId().equals(streamId)) received.add(envelope.message());
        });

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(received).containsExactly(new CartOpened(cartId), new ItemAdded(cartId, "t-shirt")));
    }

    @Test
    public void subscriberGetsAppendedEventsOfItsTypeWithTheirStreamAndLogPositions() {
        var received = new CopyOnWriteArrayList<MessageEnvelope<Object>>();
        eventStore().subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().stream().streamId().equals(streamId)) received.add(envelope);
        });

        var result = eventStore().append(streamId, new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"));

        var logPosition = ((AppendResult.Success) result).lastEvent().position();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(received).extracting(MessageEnvelope::message).containsExactly(new ItemAdded(cartId, "t-shirt"));
            assertThat(received)
                    .extracting(
                            envelope -> envelope.metadata().stream().streamId(),
                            envelope -> envelope.metadata().stream().streamPosition(),
                            envelope -> envelope.metadata().stream().logPosition())
                    .containsExactly(tuple(streamId, 1L, logPosition));
        });
    }

    @Test
    public void aSubscriptionGetsEveryEventOfItsTypesInLogOrder() {
        var received = new CopyOnWriteArrayList<Object>();
        eventStore().subscribe(subscriptionName(), List.of(CartOpened.class, ItemAdded.class), envelope -> {
            if (envelope.metadata().stream().streamId().equals(streamId)) received.add(envelope.message());
        });

        eventStore().append(streamId, new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"), new CartOpened(cartId));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(received)
                        .containsExactly(
                                new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"), new CartOpened(cartId)));
    }

    @Test
    public void subscribingWithANameAlreadyInUseIsRefusedNamingTheName() {
        var name = subscriptionName();
        eventStore().subscribe(name, List.of(CartOpened.class), envelope -> {});

        assertThatThrownBy(() -> eventStore().subscribe(name, List.of(ItemAdded.class), envelope -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(name);
    }

    @Test
    public void subscribingWithNoEventTypesIsRefusedNamingTheSubscription() {
        assertThatThrownBy(() -> eventStore().subscribe("cart-events", List.of(), envelope -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cart-events")
                .hasMessageContaining("event type");
    }

    protected String subscriptionName() {
        return "test-%s".formatted(UUID.randomUUID());
    }

    protected List<Object> eventsOf(String streamId) {
        var result = eventStore().read(streamId);

        assertThat(result).isInstanceOf(EventStore.ReadResult.Success.class);

        return ((EventStore.ReadResult.Success) result)
                .events().stream().<Object>map(MessageEnvelope::message).toList();
    }

    protected List<MessageMetadata> metadataOf(String streamId) {
        var result = eventStore().read(streamId);

        assertThat(result).isInstanceOf(EventStore.ReadResult.Success.class);

        return ((EventStore.ReadResult.Success) result)
                .events().stream().map(MessageEnvelope::metadata).toList();
    }

    public sealed interface CartEvent {}

    public record CartOpened(String cartId) implements CartEvent {}

    public record ItemAdded(String cartId, String productId) implements CartEvent {}

    public record CartOpenedAt(String cartId, OffsetDateTime openedAt) {}
}
