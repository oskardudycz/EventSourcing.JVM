package io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.mutable.solution2;

import java.util.UUID;

public interface Aggregate<ShoppingCartEvent> {
  UUID id();

  void evolve(ShoppingCartEvent event);
}
