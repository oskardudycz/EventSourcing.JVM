package io.eventdriven.uniqueness.core.resourcereservation;

import java.util.function.Supplier;

public interface ResourceReservation<R> {
  boolean reserve(R resource, Supplier<Boolean> onReserved);
}
