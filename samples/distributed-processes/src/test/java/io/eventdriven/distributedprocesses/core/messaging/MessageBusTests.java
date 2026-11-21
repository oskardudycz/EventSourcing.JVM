package io.eventdriven.distributedprocesses.core.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageMapping;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class MessageBusTests {
    private static final String KEY = "ecommerce.order.1";
    private static final Duration LATE_DELIVERY_WINDOW = Duration.ofSeconds(2);

    protected final String id = UUID.randomUUID().toString().substring(0, 8);
    protected final String orders = "orders-" + id;
    protected final List<String> log = new CopyOnWriteArrayList<>();
    protected MessageBus messageBus;
    private final List<MessageBus> buses = new ArrayList<>();

    protected abstract MessageBus createBus();

    protected void beforePublish(MessageBus bus) {}

    protected void dispose(MessageBus bus) {}

    protected static MessageMapping messageMapping() {
        return new MessageMapping()
                .register("test.OrderPlaced", OrderPlaced.class)
                .register("test.ConfirmOrder", ConfirmOrder.class)
                .register("test.OrderCanceled", OrderCanceled.class);
    }

    @BeforeEach
    void createMessageBus() {
        messageBus = newBus().routes(new MessageRoutes()
                        .routeTarget(MessageRoutes.inPackage(MessageBusTests.class.getPackageName()), orders)
                        .routeKey(OrderMessage.class, message -> KEY))
                .channel(orders)
                .bind(orders, orders, "#");
    }

    @AfterEach
    void disposeMessageBuses() {
        buses.forEach(this::dispose);
    }

    protected MessageBus newBus() {
        return track(createBus());
    }

    protected <Bus extends MessageBus> Bus track(Bus bus) {
        buses.add(bus);
        return bus;
    }

    protected void publish(MessageBus bus, Object... messages) {
        beforePublish(bus);
        bus.publish(List.of(messages));
    }

    protected void awaitLog(String... entries) {
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(log).containsExactlyInAnyOrder(entries));
        awaitLogStaysTheSame(entries);
    }

    protected void awaitLogInOrder(String... entries) {
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(log).containsExactly(entries));
        awaitLogStaysTheSame(entries);
    }

    private void awaitLogStaysTheSame(String... entries) {
        await().during(LATE_DELIVERY_WINDOW)
                .atMost(LATE_DELIVERY_WINDOW.plusSeconds(5))
                .untilAsserted(() -> assertThat(log).containsExactlyInAnyOrder(entries));
    }

    @Test
    public void deliversToAllSubscribersOfTheTypeInRegistrationOrder() {
        messageBus.subscribe(orders, OrderPlaced.class, event -> log.add("first:" + event.id()));
        messageBus.subscribe(orders, OrderPlaced.class, event -> log.add("second:" + event.id()));

        publish(messageBus, new OrderPlaced("order-1"));

        awaitLogInOrder("first:order-1", "second:order-1");
    }

    @Test
    public void commandReachesTheQueueBoundToItsChannelOnce() {
        messageBus.subscribe(orders, ConfirmOrder.class, command -> log.add("confirm"));
        messageBus.subscribe(orders, OrderPlaced.class, event -> log.add("placed"));

        publish(messageBus, new ConfirmOrder("order-1"), new OrderPlaced("order-1"));

        awaitLogInOrder("confirm", "placed");
    }

    @Test
    public void messageWithNoSubscriberInTheQueueIsSkippedAndTheNextOneIsHandled() {
        messageBus.subscribe(orders, OrderPlaced.class, event -> log.add("placed"));

        assertThatNoException()
                .isThrownBy(() -> publish(messageBus, new ConfirmOrder("order-1"), new OrderPlaced("order-1")));

        awaitLog("placed");
    }

    @Test
    public void deliversByExactRuntimeClassNotByAssignability() {
        messageBus.subscribe(orders, OrderMessage.class, message -> log.add("supertype"));
        messageBus.subscribe(orders, OrderCanceled.class, event -> log.add("canceled"));

        publish(messageBus, new OrderPlaced("order-1"), new OrderCanceled("order-1"));

        awaitLogInOrder("canceled");
    }

    @Test
    public void runsMiddlewareAlsoForMessagesWithoutSubscribers() {
        messageBus.use(message -> log.add("middleware"));

        publish(messageBus, new OrderPlaced("order-1"));

        assertThat(log).containsExactly("middleware");
    }

    @Test
    public void subscribeReturnsTheSameBusSoWiringChains() {
        var chained = messageBus
                .subscribe(orders, OrderPlaced.class, event -> log.add("placed"))
                .subscribe(orders, ConfirmOrder.class, command -> log.add("confirm"));

        assertThat(chained).isSameAs(messageBus);
    }

    @Test
    public void everyQueueBoundToTheChannelGetsTheMessage() {
        var billing = "billing-" + id;
        messageBus.bind(billing, orders, "#");
        messageBus.subscribe(orders, OrderPlaced.class, event -> log.add("orders"));
        messageBus.subscribe(billing, OrderPlaced.class, event -> log.add("billing"));

        publish(messageBus, new OrderPlaced("order-1"));

        awaitLog("orders", "billing");
    }

    @Test
    public void responseReachesOnlyTheQueueWhosePatternMatches() {
        var responses = "responses-" + id;
        var bus = newBus().routes(
                        new MessageRoutes().route(OrderPlaced.class, event -> new RoutingTarget(responses, event.id())))
                .channel(responses)
                .bind("orders-responses-" + id, responses, "ecommerce.order.*")
                .bind("invoices-responses-" + id, responses, "ecommerce.invoice.*")
                .subscribe("orders-responses-" + id, OrderPlaced.class, event -> log.add("orders:" + event.id()))
                .subscribe("invoices-responses-" + id, OrderPlaced.class, event -> log.add("invoices:" + event.id()));

        publish(bus, new OrderPlaced("ecommerce.order.1"), new OrderPlaced("ecommerce.invoice.7"));

        awaitLog("orders:ecommerce.order.1", "invoices:ecommerce.invoice.7");
    }

    @Test
    public void aStarMatchesOneWordAndAHashMatchesAnyNumberOfWords() {
        var responses = "responses-" + id;
        var bus = newBus().routes(
                        new MessageRoutes().route(OrderPlaced.class, event -> new RoutingTarget(responses, event.id())))
                .channel(responses)
                .bind("star-" + id, responses, "ecommerce.*")
                .bind("hash-" + id, responses, "ecommerce.#")
                .subscribe("star-" + id, OrderPlaced.class, event -> log.add("star:" + event.id()))
                .subscribe("hash-" + id, OrderPlaced.class, event -> log.add("hash:" + event.id()));

        publish(
                bus,
                new OrderPlaced("ecommerce"),
                new OrderPlaced("ecommerce.order"),
                new OrderPlaced("ecommerce.order.1"));

        awaitLog("hash:ecommerce", "star:ecommerce.order", "hash:ecommerce.order", "hash:ecommerce.order.1");
    }

    @Test
    public void aQueueBoundToAnotherChannelGetsNothing() {
        var payments = "payments-" + id;
        var bus = newBus().routes(new MessageRoutes()
                        .routeTarget(ConfirmOrder.class, command -> payments)
                        .routeTarget(OrderPlaced.class, event -> orders))
                .channel(orders)
                .channel(payments)
                .bind(orders, orders, "#")
                .bind(payments, payments, "#")
                .subscribe(orders, OrderPlaced.class, event -> log.add("orders"))
                .subscribe(payments, OrderPlaced.class, event -> log.add("payments:placed"))
                .subscribe(payments, ConfirmOrder.class, command -> log.add("payments:confirm"));

        publish(bus, new OrderPlaced("order-1"), new ConfirmOrder("order-1"));

        awaitLog("orders", "payments:confirm");
    }

    @Test
    public void publishingAMessageWithoutARouteFailsNamingItsType() {
        var bus = newBus().channel(orders).bind(orders, orders, "#");

        assertThatThrownBy(() -> publish(bus, new OrderPlaced("order-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(OrderPlaced.class.getName());
    }

    @Test
    public void middlewareSeesAMessageWithoutARouteBeforeThePublishFails() {
        var bus = newBus();
        bus.use(message -> log.add("middleware"));

        assertThatThrownBy(() -> publish(bus, new OrderPlaced("order-1"))).isInstanceOf(IllegalStateException.class);

        assertThat(log).containsExactly("middleware");
    }

    @Test
    public void publishingToAChannelThatIsNotDeclaredFailsNamingTheChannel() {
        var bus = newBus().routes(new MessageRoutes().routeTarget(OrderPlaced.class, event -> orders));

        assertThatThrownBy(() -> publish(bus, new OrderPlaced("order-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(orders);
    }

    @Test
    public void routesRegisteredByEachModuleAreAllUsed() {
        var commands = "commands-" + id;
        var bus = newBus().routes(new MessageRoutes().routeTarget(OrderPlaced.class, event -> orders))
                .routes(new MessageRoutes().routeTarget(ConfirmOrder.class, command -> commands))
                .channel(orders)
                .channel(commands)
                .bind(orders, orders, "#")
                .bind(commands, commands, "#")
                .subscribe(orders, OrderPlaced.class, event -> log.add("placed"))
                .subscribe(commands, ConfirmOrder.class, command -> log.add("confirm"));

        publish(bus, new OrderPlaced("order-1"), new ConfirmOrder("order-1"));

        awaitLog("placed", "confirm");
    }

    public sealed interface OrderMessage {}

    public record ConfirmOrder(String id) implements OrderMessage {}

    public record OrderPlaced(String id) implements OrderMessage {}

    public record OrderCanceled(String id) implements OrderMessage {}
}
