package io.eventdriven.uniqueness.core.resourcereservation;

import io.eventdriven.uniqueness.core.processing.HandlerWithAck;

public interface ResourceReservationHandler {
  Boolean reserve(String resourceKey, HandlerWithAck<Boolean> onReserved);

  void release(String resourceKey);
}
