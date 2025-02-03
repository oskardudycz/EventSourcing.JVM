package io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.immutable;

import io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.immutable.productItems.ProductItems;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.immutable.FunctionalTools.When;
import static io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.immutable.FunctionalTools.when;

public sealed interface ShoppingCart {
  record Initial() implements ShoppingCart {
  }

  record Pending(ProductItems ProductItems) implements ShoppingCart {
  }

  record Closed() implements ShoppingCart {

  }

  sealed interface Event {
    record Opened(
      UUID shoppingCartId,
      UUID clientId,
      OffsetDateTime openedAt
    ) implements Event {
    }

    record ProductItemAdded(
      UUID shoppingCartId,
      ProductItems.PricedProductItem productItem,
      OffsetDateTime addedAt
    ) implements Event {
    }

    record ProductItemRemoved(
      UUID shoppingCartId,
      ProductItems.PricedProductItem productItem,
      OffsetDateTime removedAt
    ) implements Event {
    }

    record Confirmed(
      UUID shoppingCartId,
      OffsetDateTime confirmedAt
    ) implements Event {
    }

    record Canceled(
      UUID shoppingCartId,
      OffsetDateTime canceledAt
    ) implements Event {
    }
  }

  static ShoppingCart evolve(ShoppingCart state, Event event) {
    return switch (when(state, event)) {
      case When(Initial initial, Event.Opened opened) ->
        new Pending(ProductItems.empty());

      case When(
        Pending(var productItems),
        Event.ProductItemAdded(var id, var productItem, var addedAt)
      ) -> new Pending(productItems.add(productItem));

      case When(
        Pending(var productItems),
        Event.ProductItemRemoved(var id, var productItem, var removedAt)
      ) -> new Pending(productItems.remove(productItem));

      case When(Pending pending, Event.Confirmed confirmed) -> new Closed();
      case When(Pending pending, Event.Canceled canceled) -> new Closed();

      default -> state;
    };
  }
}
