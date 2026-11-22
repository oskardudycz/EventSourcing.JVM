package io.eventdriven.core.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.eventdriven.core.messaging.versioning.MessageMapping;
import io.eventdriven.core.messaging.versioning.MessageSerializer;
import io.eventdriven.testing.messaging.MessageCatcher;
import io.eventdriven.testing.messaging.TestMessages;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class MessageBusTests {
    private static final String KEY = "ecommerce.order.1";
    private static final Duration CATCH_UP_TIMEOUT = Duration.ofSeconds(30);

    protected final String id = UUID.randomUUID().toString().substring(0, 8);
    protected final String orders = "orders-" + id;
    protected final MessageCatcher messages = new MessageCatcher();
    protected MessageBus messageBus;
    private final List<MessageBus> buses = new ArrayList<>();

    protected abstract MessageBus createBus(
            List<MessageRoutes> routes, List<MessageChannel> channels, List<MessageQueue> queues);

    protected void beforePublish(MessageBus bus) {}

    protected void dispose(MessageBus bus) {}

    protected static MessageMapping messageMapping() {
        return new MessageMapping()
                .trust(MessageBusTests.class.getPackageName())
                .register("test.OrderPlaced", OrderPlaced.class)
                .register("test.ConfirmOrder", ConfirmOrder.class)
                .register("test.OrderCanceled", OrderCanceled.class);
    }

    public static MessageSerializer serializer() {
        return TestMessages.serializer(messageMapping());
    }

    protected MessageRoutes ordersRoutes() {
        return new MessageRoutes()
                .routeTarget(MessageRoutes.inPackage(MessageBusTests.class.getPackageName()), orders)
                .routeKey(OrderMessage.class, message -> KEY);
    }

    @BeforeEach
    void createMessageBus() {
        messageBus = newBus(List.of(ordersRoutes()), List.of(new MessageChannel(orders)), List.of(ordersQueue()));
    }

    @AfterEach
    void disposeMessageBuses() {
        buses.forEach(this::dispose);
    }

    protected MessageQueue ordersQueue() {
        return new MessageQueue(orders, orders, "#");
    }

    protected MessageBus newBus(List<MessageRoutes> routes, List<MessageChannel> channels, List<MessageQueue> queues) {
        return track(createBus(routes, channels, queues));
    }

    protected <Bus extends MessageBus> Bus track(Bus bus) {
        buses.add(bus);
        return bus;
    }

    protected void publish(MessageBus bus, Object... messages) {
        beforePublish(bus);
        bus.publish(List.of(messages));
    }

    protected void whenCaughtUp() {
        buses.forEach(bus -> bus.whenCaughtUp());
    }

    protected abstract List<SpanData> finishedSpans();

    @Test
    public void consumerOpensAStepSpanNamedAfterTheMessageType() {
        messageBus.subscribe(orders, OrderPlaced.class, messages::catchMessage);

        publish(messageBus, new OrderPlaced("order-1"));

        whenCaughtUp();
        await().atMost(CATCH_UP_TIMEOUT)
                .untilAsserted(() -> assertThat(finishedSpans())
                        .extracting(SpanData::getName)
                        .contains("handle OrderPlaced"));
    }

    @Test
    public void deliversToAllSubscribersOfTheTypeInRegistrationOrder() {
        messageBus.subscribe(orders, OrderPlaced.class, event -> messages.catchMessage(new Handled("first", event)));
        messageBus.subscribe(orders, OrderPlaced.class, event -> messages.catchMessage(new Handled("second", event)));

        publish(messageBus, new OrderPlaced("order-1"));

        whenCaughtUp();
        messages.shouldReceiveMessages(
                new Handled("first", new OrderPlaced("order-1")), new Handled("second", new OrderPlaced("order-1")));
    }

    @Test
    public void commandReachesTheQueueBoundToItsChannelOnce() {
        messageBus.subscribe(orders, ConfirmOrder.class, messages::catchMessage);
        messageBus.subscribe(orders, OrderPlaced.class, messages::catchMessage);

        publish(messageBus, new ConfirmOrder("order-1"), new OrderPlaced("order-1"));

        whenCaughtUp();
        messages.shouldReceiveMessages(new ConfirmOrder("order-1"), new OrderPlaced("order-1"));
    }

    @Test
    public void messageWithNoSubscriberInTheQueueIsSkippedAndTheNextOneIsHandled() {
        messageBus.subscribe(orders, OrderPlaced.class, messages::catchMessage);

        assertThatNoException()
                .isThrownBy(() -> publish(messageBus, new ConfirmOrder("order-1"), new OrderPlaced("order-1")));

        whenCaughtUp();
        messages.shouldReceiveSingleEvent(new OrderPlaced("order-1"));
    }

    @Test
    public void deliversByExactRuntimeClassNotByAssignability() {
        var supertypeSubscriber = new MessageCatcher();
        messageBus.subscribe(orders, OrderMessage.class, supertypeSubscriber::catchMessage);
        messageBus.subscribe(orders, OrderCanceled.class, messages::catchMessage);

        publish(messageBus, new OrderPlaced("order-1"), new OrderCanceled("order-1"));

        whenCaughtUp();
        messages.shouldReceiveSingleEvent(new OrderCanceled("order-1"));
        supertypeSubscriber.shouldNotReceiveAnyEvent();
    }

    @Test
    public void runsMiddlewareAlsoForMessagesWithoutSubscribers() {
        messageBus.use(messages::catchMessage);

        publish(messageBus, new OrderPlaced("order-1"));

        messages.shouldReceiveSingleEvent(new OrderPlaced("order-1"));
    }

    @Test
    public void subscribeReturnsTheSameBusSoWiringChains() {
        var chained = messageBus
                .subscribe(orders, OrderPlaced.class, messages::catchMessage)
                .subscribe(orders, ConfirmOrder.class, messages::catchMessage);

        assertThat(chained).isSameAs(messageBus);
    }

    @Test
    public void everyQueueBoundToTheChannelGetsTheMessage() {
        var billing = "billing-" + id;
        var bus = newBus(
                List.of(ordersRoutes()),
                List.of(new MessageChannel(orders)),
                List.of(ordersQueue(), new MessageQueue(billing, orders, "#")));
        var billingMessages = new MessageCatcher();
        bus.subscribe(orders, OrderPlaced.class, messages::catchMessage);
        bus.subscribe(billing, OrderPlaced.class, billingMessages::catchMessage);

        publish(bus, new OrderPlaced("order-1"));

        whenCaughtUp();
        messages.shouldReceiveSingleEvent(new OrderPlaced("order-1"));
        billingMessages.shouldReceiveSingleEvent(new OrderPlaced("order-1"));
    }

    @Test
    public void responseReachesOnlyTheQueueWhosePatternMatches() {
        var responses = "responses-" + id;
        var invoices = new MessageCatcher();
        var bus = newBus(
                List.of(new MessageRoutes()
                        .route(OrderPlaced.class, event -> new RoutingTarget(responses, event.id()))),
                List.of(new MessageChannel(responses)),
                List.of(
                        new MessageQueue("orders-responses-" + id, responses, "ecommerce.order.*"),
                        new MessageQueue("invoices-responses-" + id, responses, "ecommerce.invoice.*")));
        bus.subscribe("orders-responses-" + id, OrderPlaced.class, messages::catchMessage);
        bus.subscribe("invoices-responses-" + id, OrderPlaced.class, invoices::catchMessage);

        publish(bus, new OrderPlaced("ecommerce.order.1"), new OrderPlaced("ecommerce.invoice.7"));

        whenCaughtUp();
        messages.shouldReceiveSingleEvent(new OrderPlaced("ecommerce.order.1"));
        invoices.shouldReceiveSingleEvent(new OrderPlaced("ecommerce.invoice.7"));
    }

    @Test
    public void aStarMatchesOneWordAndAHashMatchesAnyNumberOfWords() {
        var responses = "responses-" + id;
        var hash = new MessageCatcher();
        var bus = newBus(
                List.of(new MessageRoutes()
                        .route(OrderPlaced.class, event -> new RoutingTarget(responses, event.id()))),
                List.of(new MessageChannel(responses)),
                List.of(
                        new MessageQueue("star-" + id, responses, "ecommerce.*"),
                        new MessageQueue("hash-" + id, responses, "ecommerce.#")));
        bus.subscribe("star-" + id, OrderPlaced.class, messages::catchMessage);
        bus.subscribe("hash-" + id, OrderPlaced.class, hash::catchMessage);

        publish(
                bus,
                new OrderPlaced("ecommerce"),
                new OrderPlaced("ecommerce.order"),
                new OrderPlaced("ecommerce.order.1"));

        whenCaughtUp();
        messages.shouldReceiveSingleEvent(new OrderPlaced("ecommerce.order"));
        assertThat(hash.published)
                .containsExactlyInAnyOrder(
                        new OrderPlaced("ecommerce"),
                        new OrderPlaced("ecommerce.order"),
                        new OrderPlaced("ecommerce.order.1"));
    }

    @Test
    public void aQueueBoundToAnotherChannelGetsNothing() {
        var payments = "payments-" + id;
        var paymentsMessages = new MessageCatcher();
        var bus = newBus(
                List.of(new MessageRoutes()
                        .routeTarget(ConfirmOrder.class, command -> payments)
                        .routeTarget(OrderPlaced.class, event -> orders)),
                List.of(new MessageChannel(orders), new MessageChannel(payments)),
                List.of(ordersQueue(), new MessageQueue(payments, payments, "#")));
        bus.subscribe(orders, OrderPlaced.class, messages::catchMessage);
        bus.subscribe(payments, OrderPlaced.class, paymentsMessages::catchMessage);
        bus.subscribe(payments, ConfirmOrder.class, paymentsMessages::catchMessage);

        publish(bus, new OrderPlaced("order-1"), new ConfirmOrder("order-1"));

        whenCaughtUp();
        messages.shouldReceiveSingleEvent(new OrderPlaced("order-1"));
        paymentsMessages.shouldReceiveSingleEvent(new ConfirmOrder("order-1"));
    }

    @Test
    public void publishingAMessageWithoutARouteFailsNamingItsType() {
        var bus = newBus(List.of(), List.of(new MessageChannel(orders)), List.of(ordersQueue()));

        assertThatThrownBy(() -> publish(bus, new OrderPlaced("order-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(OrderPlaced.class.getName());
    }

    @Test
    public void middlewareSeesAMessageWithoutARouteBeforeThePublishFails() {
        var bus = newBus(List.of(), List.of(), List.of());
        bus.use(messages::catchMessage);

        assertThatThrownBy(() -> publish(bus, new OrderPlaced("order-1"))).isInstanceOf(IllegalStateException.class);

        messages.shouldReceiveSingleEvent(new OrderPlaced("order-1"));
    }

    @Test
    public void publishingToAChannelThatIsNotDeclaredFailsNamingTheChannel() {
        var bus = newBus(
                List.of(new MessageRoutes().routeTarget(OrderPlaced.class, event -> orders)), List.of(), List.of());

        assertThatThrownBy(() -> publish(bus, new OrderPlaced("order-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(orders);
    }

    @Test
    public void routesGivenByEachModuleAreAllUsed() {
        var commands = "commands-" + id;
        var commandMessages = new MessageCatcher();
        var bus = newBus(
                List.of(
                        new MessageRoutes().routeTarget(OrderPlaced.class, event -> orders),
                        new MessageRoutes().routeTarget(ConfirmOrder.class, command -> commands)),
                List.of(new MessageChannel(orders), new MessageChannel(commands)),
                List.of(ordersQueue(), new MessageQueue(commands, commands, "#")));
        bus.subscribe(orders, OrderPlaced.class, messages::catchMessage);
        bus.subscribe(commands, ConfirmOrder.class, commandMessages::catchMessage);

        publish(bus, new OrderPlaced("order-1"), new ConfirmOrder("order-1"));

        whenCaughtUp();
        messages.shouldReceiveSingleEvent(new OrderPlaced("order-1"));
        commandMessages.shouldReceiveSingleEvent(new ConfirmOrder("order-1"));
    }

    public sealed interface OrderMessage {}

    public record ConfirmOrder(String id) implements OrderMessage {}

    public record OrderPlaced(String id) implements OrderMessage {}

    public record OrderCanceled(String id) implements OrderMessage {}

    public record Handled(String by, Object message) {}

    public sealed interface PaymentCommand {
        String paymentId();

        record CapturePayment(String paymentId) implements PaymentCommand {}

        record CancelPayment(String paymentId) implements PaymentCommand {}
    }

    public sealed interface PaymentEvent {
        String referenceId();

        record PaymentAuthorized(String referenceId, OffsetDateTime authorizedAt) implements PaymentEvent {}
    }
}
