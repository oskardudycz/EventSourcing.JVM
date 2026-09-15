package io.eventdriven.distributedprocesses.core.messaging;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;

public class InMemoryCommandBusTests {
  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final ArrayList<String> log = new ArrayList<>();

  @Test
  public void sendsCommandToItsHandler() {
    commandBus.handle(PlaceOrder.class, command -> log.add("placed:" + command.id()));

    commandBus.send(new PlaceOrder("order-1"));

    assertThat(log).containsExactly("placed:order-1");
  }

  @Test
  public void sendsCommandsInTheOrderTheyWereGiven() {
    commandBus.handle(PlaceOrder.class, command -> log.add("placed"));
    commandBus.handle(ConfirmOrder.class, command -> log.add("confirmed"));

    commandBus.send(new ConfirmOrder("order-1"), new PlaceOrder("order-1"));

    assertThat(log).containsExactly("confirmed", "placed");
  }

  @Test
  public void secondHandlerForTheSameCommandTypeThrowsNamingTheType() {
    commandBus.handle(PlaceOrder.class, command -> log.add("first"));

    assertThatThrownBy(() -> commandBus.handle(PlaceOrder.class, command -> log.add("second")))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining(PlaceOrder.class.getName());

    commandBus.send(new PlaceOrder("order-1"));

    assertThat(log).containsExactly("first");
  }

  @Test
  public void sendingCommandWithoutHandlerThrowsNamingTheType() {
    commandBus.handle(PlaceOrder.class, command -> log.add("placed"));

    assertThatThrownBy(() -> commandBus.send(new ConfirmOrder("order-1")))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining(ConfirmOrder.class.getName());

    assertThat(log).isEmpty();
  }

  @Test
  public void dispatchesByExactRuntimeClassNotByAssignability() {
    commandBus.handle(OrderCommand.class, command -> log.add("supertype"));

    assertThatThrownBy(() -> commandBus.send(new PlaceOrder("order-1")))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining(PlaceOrder.class.getName());

    assertThat(log).isEmpty();
  }

  @Test
  public void dispatchesDepthFirstSoNestedSendCompletesBeforeHandlerReturns() {
    commandBus.handle(PlaceOrder.class, command -> {
      log.add("placed:start");
      commandBus.send(new ConfirmOrder(command.id()));
      log.add("placed:end");
    });
    commandBus.handle(ConfirmOrder.class, command -> log.add("confirmed"));
    commandBus.handle(CancelOrder.class, command -> log.add("canceled"));

    commandBus.send(new PlaceOrder("order-1"), new CancelOrder("order-1"));

    assertThat(log)
      .containsExactly("placed:start", "confirmed", "placed:end", "canceled");
  }

  @Test
  public void runsMiddlewareForEveryCommandBeforeHandlersInRegistrationOrder() {
    commandBus.use(message -> log.add("first middleware:" + message.getClass().getSimpleName()));
    commandBus.use(message -> log.add("second middleware:" + message.getClass().getSimpleName()));
    commandBus.handle(PlaceOrder.class, command -> log.add("placed"));
    commandBus.handle(ConfirmOrder.class, command -> log.add("confirmed"));

    commandBus.send(new PlaceOrder("order-1"), new ConfirmOrder("order-1"));

    assertThat(log).containsExactly(
      "first middleware:PlaceOrder",
      "second middleware:PlaceOrder",
      "placed",
      "first middleware:ConfirmOrder",
      "second middleware:ConfirmOrder",
      "confirmed"
    );
  }

  @Test
  public void handleAndUseReturnTheSameBusSoWiringChains() {
    var chained = commandBus
      .handle(PlaceOrder.class, command -> log.add("placed"))
      .handle(ConfirmOrder.class, command -> log.add("confirmed"))
      .use(message -> log.add("middleware"));

    assertThat(chained).isSameAs(commandBus);
  }

  sealed interface OrderCommand {
  }

  record PlaceOrder(String id) implements OrderCommand {
  }

  record ConfirmOrder(String id) implements OrderCommand {
  }

  record CancelOrder(String id) implements OrderCommand {
  }
}
