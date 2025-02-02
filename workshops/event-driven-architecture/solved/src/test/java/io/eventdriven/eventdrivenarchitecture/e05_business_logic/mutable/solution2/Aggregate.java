package io.eventdriven.eventdrivenarchitecture.e05_business_logic.mutable.solution2;

import java.util.UUID;

public interface Aggregate<ShoppingCartEvent> {
  UUID id();

  void evolve(ShoppingCartEvent event);
}
