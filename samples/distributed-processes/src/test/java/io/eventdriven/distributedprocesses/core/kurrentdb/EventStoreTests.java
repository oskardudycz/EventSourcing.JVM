package io.eventdriven.distributedprocesses.core.kurrentdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.kurrent.dbclient.StreamState;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public abstract class EventStoreTests {
    protected final String cartId = UUID.randomUUID().toString();
    protected final String streamId = "ShoppingCart-%s".formatted(cartId);

    protected abstract EventStore eventStore();

    @Test
    public void appendedEventsComeBackInOrder() {
        var opened = new CartOpened(cartId);
        var added = new ItemAdded(cartId, "t-shirt");
        var third = new ItemAdded(cartId, "hoodie");

        eventStore().append(streamId, opened, added, third);

        assertThat(eventsOf(streamId)).containsExactly(opened, added, third);
    }

    @Test
    public void secondAppendWithTheSameExpectedRevisionConflictsAndAppendsNothing() {
        eventStore()
                .append(
                        streamId,
                        new CartOpened(cartId),
                        new ItemAdded(cartId, "t-shirt"),
                        new ItemAdded(cartId, "hoodie"));

        var first = eventStore().append(streamId, StreamState.streamRevision(2), new ItemAdded(cartId, "socks"));
        var second = eventStore().append(streamId, StreamState.streamRevision(2), new ItemAdded(cartId, "scarf"));

        assertThat(first.succeeded()).isTrue();
        assertThat(second).isInstanceOf(EventStore.AppendResult.Conflict.class);
        assertThat(eventsOf(streamId))
                .containsExactly(
                        new CartOpened(cartId),
                        new ItemAdded(cartId, "t-shirt"),
                        new ItemAdded(cartId, "hoodie"),
                        new ItemAdded(cartId, "socks"));
    }

    @Test
    public void failedEventIsDeliveredAgainBeforeNewerEvents() {
        var attempts = new AtomicInteger();
        var received = new CopyOnWriteArrayList<Object>();
        eventStore().subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (!envelope.metadata().streamId().equals(streamId)) return;

            received.add(envelope.data());
            if (attempts.getAndIncrement() == 0) throw new IllegalStateException("projection is down");
        });

        eventStore().append(streamId, new ItemAdded(cartId, "t-shirt"));
        eventStore().append(streamId, StreamState.any(), new ItemAdded(cartId, "hoodie"));

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
            if (envelope.metadata().streamId().equals(streamId)) received.add(envelope.data());
        });

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(received).containsExactly(new CartOpened(cartId), new ItemAdded(cartId, "t-shirt")));
    }

    @Test
    public void subscriberGetsAppendedEventsOfItsTypeWithTheirStreamAndLogPositions() {
        var received = new CopyOnWriteArrayList<EventEnvelope<Object>>();
        eventStore().subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().streamId().equals(streamId)) received.add(envelope);
        });

        var result = eventStore().append(streamId, new CartOpened(cartId), new ItemAdded(cartId, "t-shirt"));

        var logPosition =
                ((EventStore.AppendResult.Success) result).logPosition().getCommitUnsigned();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(received).extracting(EventEnvelope::data).containsExactly(new ItemAdded(cartId, "t-shirt"));
            assertThat(received)
                    .extracting(
                            envelope -> envelope.metadata().streamId(),
                            envelope -> envelope.metadata().streamPosition(),
                            envelope -> envelope.metadata().logPosition())
                    .containsExactly(tuple(streamId, 1L, logPosition));
        });
    }

    @Test
    public void aSubscriptionGetsEveryEventOfItsTypesInLogOrder() {
        var received = new CopyOnWriteArrayList<Object>();
        eventStore().subscribe(subscriptionName(), List.of(CartOpened.class, ItemAdded.class), envelope -> {
            if (envelope.metadata().streamId().equals(streamId)) received.add(envelope.data());
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
                .events().stream().<Object>map(EventEnvelope::data).toList();
    }

    public record CartOpened(String cartId) {}

    public record ItemAdded(String cartId, String productId) {}
}
