package io.eventdriven.distributedprocesses.core.messaging;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class InMemoryMessageBusTests {
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
    private final ArrayList<String> log = new ArrayList<>();

    public InMemoryMessageBusTests() {
        messageBus
                .routes(new MessageRoutes()
                        .routeTarget(MessageRoutes.inPackage(getClass().getPackageName()), "orders"))
                .bind("orders", "orders", "#");
    }

    @Test
    public void deliversToAllSubscribersOfTheTypeInRegistrationOrder() {
        messageBus.subscribe("orders", OrderPlaced.class, event -> log.add("first:" + event.id()));
        messageBus.subscribe("orders", OrderPlaced.class, event -> log.add("second:" + event.id()));

        messageBus.publish(List.of(new OrderPlaced("order-1")));

        assertThat(log).containsExactly("first:order-1", "second:order-1");
    }

    @Test
    public void deliversMessagesInTheOrderTheyWereGiven() {
        messageBus.subscribe("orders", OrderPlaced.class, event -> log.add("placed"));
        messageBus.subscribe("orders", ConfirmOrder.class, command -> log.add("confirm"));

        messageBus.publish(List.of(new ConfirmOrder("order-1"), new OrderPlaced("order-1")));

        assertThat(log).containsExactly("confirm", "placed");
    }

    @Test
    public void messageWithoutSubscribersIsNoOp() {
        messageBus.subscribe("orders", OrderPlaced.class, event -> log.add("placed"));

        assertThatNoException().isThrownBy(() -> messageBus.publish(List.of(new ConfirmOrder("order-1"))));

        assertThat(log).isEmpty();
    }

    @Test
    public void deliversByExactRuntimeClassNotByAssignability() {
        messageBus.subscribe("orders", OrderMessage.class, message -> log.add("supertype"));

        messageBus.publish(List.of(new OrderPlaced("order-1")));

        assertThat(log).isEmpty();
    }

    @Test
    public void deliversDepthFirstSoNestedPublishCompletesBeforeSubscriberReturns() {
        messageBus.subscribe("orders", OrderPlaced.class, event -> {
            log.add("placed:start");
            messageBus.publish(List.of(new ConfirmOrder(event.id())));
            log.add("placed:end");
        });
        messageBus.subscribe("orders", ConfirmOrder.class, command -> log.add("confirm"));
        messageBus.subscribe("orders", OrderCanceled.class, event -> log.add("canceled"));

        messageBus.publish(List.of(new OrderPlaced("order-1"), new OrderCanceled("order-1")));

        assertThat(log).containsExactly("placed:start", "confirm", "placed:end", "canceled");
    }

    @Test
    public void runsMiddlewareForEveryMessageBeforeSubscribersInRegistrationOrder() {
        messageBus.use(
                message -> log.add("first middleware:" + message.getClass().getSimpleName()));
        messageBus.use(
                message -> log.add("second middleware:" + message.getClass().getSimpleName()));
        messageBus.subscribe("orders", OrderPlaced.class, event -> log.add("placed"));
        messageBus.subscribe("orders", ConfirmOrder.class, command -> log.add("confirm"));

        messageBus.publish(List.of(new OrderPlaced("order-1"), new ConfirmOrder("order-1")));

        assertThat(log)
                .containsExactly(
                        "first middleware:OrderPlaced",
                        "second middleware:OrderPlaced",
                        "placed",
                        "first middleware:ConfirmOrder",
                        "second middleware:ConfirmOrder",
                        "confirm");
    }

    @Test
    public void runsMiddlewareAlsoForMessagesWithoutSubscribers() {
        messageBus.use(message -> log.add("middleware"));

        messageBus.publish(List.of(new OrderPlaced("order-1")));

        assertThat(log).containsExactly("middleware");
    }

    @Test
    public void subscribeReturnsTheSameBusSoWiringChains() {
        var chained = messageBus
                .subscribe("orders", OrderPlaced.class, event -> log.add("placed"))
                .subscribe("orders", ConfirmOrder.class, command -> log.add("confirm"));

        assertThat(chained).isSameAs(messageBus);
    }

    @Test
    public void everyQueueBoundToTheChannelGetsTheMessage() {
        messageBus.bind("billing", "orders", "#");
        messageBus.subscribe("orders", OrderPlaced.class, event -> log.add("orders"));
        messageBus.subscribe("billing", OrderPlaced.class, event -> log.add("billing"));

        messageBus.publish(List.of(new OrderPlaced("order-1")));

        assertThat(log).containsExactly("orders", "billing");
    }

    @Test
    public void aQueueGetsOnlyTheMessagesWhoseKeyMatchesItsPattern() {
        var bus = new InMemoryMessageBus()
                .routes(new MessageRoutes()
                        .route(OrderPlaced.class, event -> new RoutingTarget("responses", event.id())))
                .bind("orders-responses", "responses", "ecommerce.order.*")
                .bind("invoices-responses", "responses", "ecommerce.invoice.*")
                .subscribe("orders-responses", OrderPlaced.class, event -> log.add("orders:" + event.id()))
                .subscribe("invoices-responses", OrderPlaced.class, event -> log.add("invoices:" + event.id()));

        bus.publish(List.of(new OrderPlaced("ecommerce.order.1"), new OrderPlaced("ecommerce.invoice.7")));

        assertThat(log).containsExactly("orders:ecommerce.order.1", "invoices:ecommerce.invoice.7");
    }

    @Test
    public void aStarMatchesOneWordAndAHashMatchesAnyNumberOfWords() {
        var bus = new InMemoryMessageBus()
                .routes(new MessageRoutes()
                        .route(OrderPlaced.class, event -> new RoutingTarget("responses", event.id())))
                .bind("star", "responses", "ecommerce.*")
                .bind("hash", "responses", "ecommerce.#")
                .subscribe("star", OrderPlaced.class, event -> log.add("star:" + event.id()))
                .subscribe("hash", OrderPlaced.class, event -> log.add("hash:" + event.id()));

        bus.publish(List.of(
                new OrderPlaced("ecommerce"),
                new OrderPlaced("ecommerce.order"),
                new OrderPlaced("ecommerce.order.1")));

        assertThat(log)
                .containsExactly(
                        "hash:ecommerce", "star:ecommerce.order", "hash:ecommerce.order", "hash:ecommerce.order.1");
    }

    @Test
    public void aQueueBoundToAnotherChannelGetsNothing() {
        messageBus.bind("payments", "payments", "#");
        messageBus.subscribe("payments", OrderPlaced.class, event -> log.add("payments"));

        messageBus.publish(List.of(new OrderPlaced("order-1")));

        assertThat(log).isEmpty();
    }

    @Test
    public void aMessageWithoutARouteReachesOnlyTheMiddleware() {
        var bus = new InMemoryMessageBus();
        bus.use(message -> log.add("middleware"));
        bus.bind("orders", "orders", "#").subscribe("orders", OrderPlaced.class, event -> log.add("placed"));

        bus.publish(List.of(new OrderPlaced("order-1")));

        assertThat(log).containsExactly("middleware");
    }

    @Test
    public void routesRegisteredByEachModuleAreAllUsed() {
        var bus = new InMemoryMessageBus()
                .routes(new MessageRoutes().routeTarget(OrderPlaced.class, event -> "orders"))
                .routes(new MessageRoutes().routeTarget(ConfirmOrder.class, command -> "commands"))
                .bind("orders", "orders", "#")
                .bind("commands", "commands", "#")
                .subscribe("orders", OrderPlaced.class, event -> log.add("placed"))
                .subscribe("commands", ConfirmOrder.class, command -> log.add("confirm"));

        bus.publish(List.of(new OrderPlaced("order-1"), new ConfirmOrder("order-1")));

        assertThat(log).containsExactly("placed", "confirm");
    }

    sealed interface OrderMessage {}

    record ConfirmOrder(String id) implements OrderMessage {}

    record OrderPlaced(String id) implements OrderMessage {}

    record OrderCanceled(String id) implements OrderMessage {}
}
