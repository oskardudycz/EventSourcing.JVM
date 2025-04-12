package io.eventdriven.eventstores;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class EventTypeMapper {
  private static final EventTypeMapper instance = new EventTypeMapper();

  private final Map<String, Optional<Class>> typeMap = new LinkedHashMap<>();
  private final Map<Class, String> typeNameMap = new LinkedHashMap<>();

  public static String toName(Class eventType) {
    return instance.typeNameMap.computeIfAbsent(
      eventType,
      Class::getTypeName
    );
  }

  public static Optional<Class> toClass(String eventTypeName) {
    return instance.typeMap.computeIfAbsent(
      eventTypeName,
      c -> {
        try {
          return Optional.of(Class.forName(eventTypeName));
        } catch (ClassNotFoundException e) {
          return Optional.empty();
        }
      }
    );
  }
}

