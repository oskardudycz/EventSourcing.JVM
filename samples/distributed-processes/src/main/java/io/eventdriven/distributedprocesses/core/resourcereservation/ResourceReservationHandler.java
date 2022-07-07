package io.eventdriven.distributedprocesses.core.resourcereservation;

import io.eventdriven.distributedprocesses.core.processing.HandlerWithAck;

public interface ResourceReservationHandler {
  Boolean reserve(String resourceKey, HandlerWithAck<Boolean> onReserved);

  void release(String resourceKey);
}
