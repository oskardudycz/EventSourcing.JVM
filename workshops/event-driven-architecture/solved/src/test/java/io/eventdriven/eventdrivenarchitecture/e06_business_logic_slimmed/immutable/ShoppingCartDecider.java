package io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.immutable;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.immutable.FunctionalTools.On;
import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.immutable.FunctionalTools.on;
import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.immutable.ShoppingCart.Initial;
import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.immutable.ShoppingCart.Pending;
import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.immutable.ShoppingCart.Event.*;
import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.immutable.ShoppingCartDecider.Command.*;
import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.immutable.productItems.ProductItems.PricedProductItem;

public class ShoppingCartDecider {
  public sealed interface Command {
    record Open(
      UUID shoppingCartId,
      UUID clientId,
      OffsetDateTime now
    ) implements Command {
    }

    record AddProductItem(
      UUID shoppingCartId,
      PricedProductItem productItem,
      OffsetDateTime now
    ) implements Command {
    }

    record RemoveProductItem(
      UUID shoppingCartId,
      PricedProductItem productItem,
      OffsetDateTime now
    ) implements Command {
    }

    record Confirm(
      UUID shoppingCartId,
      OffsetDateTime now
    ) implements Command {
    }

    record Cancel(
      UUID shoppingCartId,
      OffsetDateTime now
    ) implements Command {
    }
  }

  public static ShoppingCart.Event decide(Command command, ShoppingCart state) {
    return switch (on(state, command)) {
      case On(Initial _, Open(var id, var clientId, var now)) ->
        new Opened(id, clientId, now);

      case On(
        Pending _,
        AddProductItem(var id, var productItem, var now)
      ) -> new ProductItemAdded(id, productItem, now);

      case On(
        Pending(var productItems),
        RemoveProductItem(var id, var productItem, var now)
      ) -> {
        if (!productItems.hasEnough(productItem))
          throw new IllegalStateException("Not enough product items to remove");

        yield new ProductItemRemoved(id, productItem, now);
      }

      case On(Pending _, Confirm(var id, var now)) ->
        new Confirmed(id, now);

      case On(Pending _, Cancel(var id, var now)) ->
        new Canceled(id, now);

      default -> throw new IllegalStateException(
        String.format("Cannot %s on %s", command.getClass().getName(), state.getClass().getName())
      );
    };
  }
}
