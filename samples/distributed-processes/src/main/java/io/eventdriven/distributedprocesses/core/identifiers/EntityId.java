package io.eventdriven.distributedprocesses.core.identifiers;

public interface EntityId {
  String value();

  default String tail() {
    return Urns.tailOf(value());
  }
}
