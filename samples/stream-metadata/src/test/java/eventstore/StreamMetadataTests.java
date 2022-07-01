package eventstore;

import com.eventstore.dbclient.*;
import org.junit.jupiter.api.*;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class StreamMetadataTests {
    enum OrderStatus { Initiated, Paid, Shipped, Closed }
    record OrderInitiated (UUID orderId, String orderNumber) {}
    record OrderStatusChanged(UUID orderId, OrderStatus status) {}
    record OrderClosed (UUID orderId) {}

    private EventStoreDBClient eventStore;

    @BeforeEach
    void beforeEach() throws ParseError {
        EventStoreDBClientSettings settings = EventStoreDBConnectionString.parse("esdb://localhost:2113?tls=false");
        this.eventStore = EventStoreDBClient.create(settings);
    }

    @Test
    void maxCountShouldRestrictNumberOfReadStreamEvents() throws ExecutionException, InterruptedException {
        final var orderId = UUID.randomUUID();
        final var streamName = "orders-%s".formatted(orderId);

        final var orderInitiated = EventData.builderAsJson(
            "order-initiated",
            new OrderInitiated(orderId, "ORD/123")
        ).build();
        final var orderPaid = EventData.builderAsJson(
            "order-paid",
            new OrderStatusChanged(orderId, OrderStatus.Paid)
        ).build();
        final var orderShipped = EventData.builderAsJson(
            "order-shipped",
                new OrderStatusChanged(orderId, OrderStatus.Shipped)
        ).build();
        final var orderClosed = EventData.builderAsJson(
            "order-closed",
                new OrderClosed(orderId)
        ).build();

        ///////////////////////////////////////////////////////////////////////////////////
        // 1. We can set stream metadata in advance
        ///////////////////////////////////////////////////////////////////////////////////
        final var streamMetadata = new StreamMetadata();
        streamMetadata.setMaxCount(2);

        this.eventStore.setStreamMetadata(streamName, streamMetadata).get();

        ///////////////////////////////////////////////////////////////////////////////////
        // 2. Append two events.
        ///////////////////////////////////////////////////////////////////////////////////
        this.eventStore.appendToStream(streamName, orderInitiated, orderPaid).get();

        var result = this.eventStore.readStream(streamName).get().getEvents();
        // we should get both of them
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(orderInitiated.getEventId(), result.get(0).getEvent().getEventId());
        Assertions.assertEquals(orderPaid.getEventId(), result.get(1).getEvent().getEventId());

        ///////////////////////////////////////////////////////////////////////////////////
        // 3. Let's add the third event. The first should not be available.
        ///////////////////////////////////////////////////////////////////////////////////
        this.eventStore.appendToStream(streamName, orderShipped).get();

        result = this.eventStore.readStream(streamName).get().getEvents();
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(orderPaid.getEventId(), result.get(0).getEvent().getEventId());
        Assertions.assertEquals(orderShipped.getEventId(), result.get(1).getEvent().getEventId());

        ///////////////////////////////////////////////////////////////////////////////////
        // 4. Let's add the forth event. The first and the second should not be available.
        ///////////////////////////////////////////////////////////////////////////////////
        this.eventStore.appendToStream(streamName, orderClosed).get();

        result = this.eventStore.readStream(streamName).get().getEvents();
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(orderShipped.getEventId(), result.get(0).getEvent().getEventId());
        Assertions.assertEquals(orderClosed.getEventId(), result.get(1).getEvent().getEventId());
    }

    @Test
    void maxAgeShouldRestrictNumberOfReadStreamEvents() throws ExecutionException, InterruptedException {
        final var orderId = UUID.randomUUID();
        final var streamName = "orders-%s".formatted(orderId);

        final var orderInitiated = EventData.builderAsJson(
                "order-initiated",
                new OrderInitiated(orderId, "ORD/123")
        ).build();
        final var orderPaid = EventData.builderAsJson(
                "order-paid",
                new OrderStatusChanged(orderId, OrderStatus.Paid)
        ).build();

        ///////////////////////////////////////////////////////////////////////////////////
        // 1. We can set stream metadata in advance
        ///////////////////////////////////////////////////////////////////////////////////
        final var maxAge = 2;
        final var streamMetadata = new StreamMetadata();
        streamMetadata.setMaxAge(maxAge);

        this.eventStore.setStreamMetadata(streamName, streamMetadata).get();

        ///////////////////////////////////////////////////////////////////////////////////
        // 2. Append two events.
        ///////////////////////////////////////////////////////////////////////////////////
        this.eventStore.appendToStream(streamName, orderInitiated, orderPaid).get();

        var result = this.eventStore.readStream(streamName).get().getEvents();
        // we should get both of them
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(orderInitiated.getEventId(), result.get(0).getEvent().getEventId());
        Assertions.assertEquals(orderPaid.getEventId(), result.get(1).getEvent().getEventId());

        ///////////////////////////////////////////////////////////////////////////////////
        // 3. Let's wait longer than maxAge. The first should not be available.
        ///////////////////////////////////////////////////////////////////////////////////

        Thread.sleep(3000);

        result = this.eventStore.readStream(streamName).get().getEvents();
        Assertions.assertEquals(0, result.size());
    }

    @Test
    void truncateBeforeShouldRestrictNumberOfReadStreamEvents() throws ExecutionException, InterruptedException {
        final var orderId = UUID.randomUUID();
        final var streamName = "orders-%s".formatted(orderId);

        final var orderInitiated = EventData.builderAsJson(
                "order-initiated",
                new OrderInitiated(orderId, "ORD/123")
        ).build();
        final var orderPaid = EventData.builderAsJson(
                "order-paid",
                new OrderStatusChanged(orderId, OrderStatus.Paid)
        ).build();
        final var orderShipped = EventData.builderAsJson(
                "order-shipped",
                new OrderStatusChanged(orderId, OrderStatus.Shipped)
        ).build();
        final var orderClosed = EventData.builderAsJson(
                "order-closed",
                new OrderClosed(orderId)
        ).build();

        ///////////////////////////////////////////////////////////////////////////////////
        // 1. Append four events.
        ///////////////////////////////////////////////////////////////////////////////////
        this.eventStore.appendToStream(streamName, orderInitiated, orderPaid, orderShipped, orderClosed).get();

        var result = this.eventStore.readStream(streamName).get().getEvents();
        // we should get both of them
        Assertions.assertEquals(4, result.size());
        Assertions.assertEquals(orderInitiated.getEventId(), result.get(0).getEvent().getEventId());
        Assertions.assertEquals(orderPaid.getEventId(), result.get(1).getEvent().getEventId());
        Assertions.assertEquals(orderShipped.getEventId(), result.get(2).getEvent().getEventId());
        Assertions.assertEquals(orderClosed.getEventId(), result.get(3).getEvent().getEventId());

        ///////////////////////////////////////////////////////////////////////////////////
        // 2. Truncate events before order shipped (so initiated and paid)
        ///////////////////////////////////////////////////////////////////////////////////
        final var streamMetadata = new StreamMetadata();
        streamMetadata.setTruncateBefore(2);

        this.eventStore.setStreamMetadata(streamName, streamMetadata).get();

        ///////////////////////////////////////////////////////////////////////////////////
        // 3. Let's read events again, we should not see initiated and paid events
        ///////////////////////////////////////////////////////////////////////////////////

        result = this.eventStore.readStream(streamName).get().getEvents();
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(orderShipped.getEventId(), result.get(0).getEvent().getEventId());
        Assertions.assertEquals(orderClosed.getEventId(), result.get(1).getEvent().getEventId());
    }
}
