package io.eventdriven.distributedprocesses.core.messaging;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

public class InMemoryMessageBusTests {
  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
  private final ArrayList<String> log = new ArrayList<>();

  @Test
  public void deliversToAllSubscribersOfTheTypeInRegistrationOrder() {
    messageBus.subscribe(OrderPlaced.class, event -> log.add("first:" + event.id()));
    messageBus.subscribe(OrderPlaced.class, event -> log.add("second:" + event.id()));

    messageBus.publish(List.of(new OrderPlaced("order-1")));

    assertThat(log).containsExactly("first:order-1", "second:order-1");
  }

  @Test
  public void deliversMessagesInTheOrderTheyWereGiven() {
    messageBus.subscribe(OrderPlaced.class, event -> log.add("placed"));
    messageBus.subscribe(ConfirmOrder.class, command -> log.add("confirm"));

    messageBus.publish(List.of(new ConfirmOrder("order-1"), new OrderPlaced("order-1")));

    assertThat(log).containsExactly("confirm", "placed");
  }

  @Test
  public void messageWithoutSubscribersIsNoOp() {
    messageBus.subscribe(OrderPlaced.class, event -> log.add("placed"));

    assertThatNoException()
      .isThrownBy(() -> messageBus.publish(List.of(new ConfirmOrder("order-1"))));

    assertThat(log).isEmpty();
  }

  @Test
  public void deliversByExactRuntimeClassNotByAssignability() {
    messageBus.subscribe(OrderMessage.class, message -> log.add("supertype"));

    messageBus.publish(List.of(new OrderPlaced("order-1")));

    assertThat(log).isEmpty();
  }

  @Test
  public void deliversDepthFirstSoNestedPublishCompletesBeforeSubscriberReturns() {
    messageBus.subscribe(OrderPlaced.class, event -> {
      log.add("placed:start");
      messageBus.publish(List.of(new ConfirmOrder(event.id())));
      log.add("placed:end");
    });
    messageBus.subscribe(ConfirmOrder.class, command -> log.add("confirm"));
    messageBus.subscribe(OrderCanceled.class, event -> log.add("canceled"));

    messageBus.publish(List.of(new OrderPlaced("order-1"), new OrderCanceled("order-1")));

    assertThat(log)
      .containsExactly("placed:start", "confirm", "placed:end", "canceled");
  }

  @Test
  public void runsMiddlewareForEveryMessageBeforeSubscribersInRegistrationOrder() {
    messageBus.use(message -> log.add("first middleware:" + message.getClass().getSimpleName()));
    messageBus.use(message -> log.add("second middleware:" + message.getClass().getSimpleName()));
    messageBus.subscribe(OrderPlaced.class, event -> log.add("placed"));
    messageBus.subscribe(ConfirmOrder.class, command -> log.add("confirm"));

    messageBus.publish(List.of(new OrderPlaced("order-1"), new ConfirmOrder("order-1")));

    assertThat(log).containsExactly(
      "first middleware:OrderPlaced",
      "second middleware:OrderPlaced",
      "placed",
      "first middleware:ConfirmOrder",
      "second middleware:ConfirmOrder",
      "confirm"
    );
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
      .subscribe(OrderPlaced.class, event -> log.add("placed"))
      .subscribe(ConfirmOrder.class, command -> log.add("confirm"));

    assertThat(chained).isSameAs(messageBus);
  }

  sealed interface OrderMessage {
  }

  record ConfirmOrder(String id) implements OrderMessage {
  }

  record OrderPlaced(String id) implements OrderMessage {
  }

  record OrderCanceled(String id) implements OrderMessage {
  }
}
