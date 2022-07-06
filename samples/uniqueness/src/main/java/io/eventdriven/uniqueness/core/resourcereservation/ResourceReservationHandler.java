package io.eventdriven.uniqueness.core.resourcereservation;

import java.util.function.Consumer;

public interface ResourceReservationHandler {
  void reserve(String resourceKey, Consumer<Consumer<Boolean>> onReserved);

  void release(String resourceKey);
}
