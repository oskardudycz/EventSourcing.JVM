package io.eventdriven.core.identifiers;

public interface EntityId {
    String value();

    default String tail() {
        return Urns.tailOf(value());
    }
}
