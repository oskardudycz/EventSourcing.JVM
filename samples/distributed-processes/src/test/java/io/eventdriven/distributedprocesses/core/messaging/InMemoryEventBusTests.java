package io.eventdriven.distributedprocesses.core.messaging;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;

public class InMemoryEventBusTests {
  private final InMemoryEventBus eventBus = new InMemoryEventBus();
  private final ArrayList<String> log = new ArrayList<>();

  @Test
  public void publishesToAllHandlersOfTheTypeInRegistrationOrder() {
    eventBus.subscribe(OrderPlaced.class, event -> log.add("first:" + event.id()));
    eventBus.subscribe(OrderPlaced.class, event -> log.add("second:" + event.id()));

    eventBus.publish(new OrderPlaced("order-1"));

    assertThat(log).containsExactly("first:order-1", "second:order-1");
  }

  @Test
  public void publishesEventsInTheOrderTheyWereGiven() {
    eventBus.subscribe(OrderPlaced.class, event -> log.add("placed"));
    eventBus.subscribe(OrderConfirmed.class, event -> log.add("confirmed"));

    eventBus.publish(new OrderConfirmed("order-1"), new OrderPlaced("order-1"));

    assertThat(log).containsExactly("confirmed", "placed");
  }

  @Test
  public void publishingEventWithoutSubscribersIsNoOp() {
    eventBus.subscribe(OrderPlaced.class, event -> log.add("placed"));

    assertThatNoException()
      .isThrownBy(() -> eventBus.publish(new OrderConfirmed("order-1")));

    assertThat(log).isEmpty();
  }

  @Test
  public void dispatchesByExactRuntimeClassNotByAssignability() {
    eventBus.subscribe(OrderEvent.class, event -> log.add("supertype"));

    eventBus.publish(new OrderPlaced("order-1"));

    assertThat(log).isEmpty();
  }

  @Test
  public void dispatchesDepthFirstSoNestedPublishCompletesBeforeHandlerReturns() {
    eventBus.subscribe(OrderPlaced.class, event -> {
      log.add("placed:start");
      eventBus.publish(new OrderConfirmed(event.id()));
      log.add("placed:end");
    });
    eventBus.subscribe(OrderConfirmed.class, event -> log.add("confirmed"));
    eventBus.subscribe(OrderCanceled.class, event -> log.add("canceled"));

    eventBus.publish(new OrderPlaced("order-1"), new OrderCanceled("order-1"));

    assertThat(log)
      .containsExactly("placed:start", "confirmed", "placed:end", "canceled");
  }

  @Test
  public void runsMiddlewareForEveryEventBeforeHandlersInRegistrationOrder() {
    eventBus.use(message -> log.add("first middleware:" + message.getClass().getSimpleName()));
    eventBus.use(message -> log.add("second middleware:" + message.getClass().getSimpleName()));
    eventBus.subscribe(OrderPlaced.class, event -> log.add("placed"));
    eventBus.subscribe(OrderConfirmed.class, event -> log.add("confirmed"));

    eventBus.publish(new OrderPlaced("order-1"), new OrderConfirmed("order-1"));

    assertThat(log).containsExactly(
      "first middleware:OrderPlaced",
      "second middleware:OrderPlaced",
      "placed",
      "first middleware:OrderConfirmed",
      "second middleware:OrderConfirmed",
      "confirmed"
    );
  }

  @Test
  public void runsMiddlewareAlsoForEventsWithoutSubscribers() {
    eventBus.use(message -> log.add("middleware"));

    eventBus.publish(new OrderPlaced("order-1"));

    assertThat(log).containsExactly("middleware");
  }

  @Test
  public void subscribeAndUseReturnTheSameBusSoWiringChains() {
    var chained = eventBus
      .subscribe(OrderPlaced.class, event -> log.add("placed"))
      .subscribe(OrderConfirmed.class, event -> log.add("confirmed"))
      .use(message -> log.add("middleware"));

    assertThat(chained).isSameAs(eventBus);
  }

  sealed interface OrderEvent {
  }

  record OrderPlaced(String id) implements OrderEvent {
  }

  record OrderConfirmed(String id) implements OrderEvent {
  }

  record OrderCanceled(String id) implements OrderEvent {
  }
}
