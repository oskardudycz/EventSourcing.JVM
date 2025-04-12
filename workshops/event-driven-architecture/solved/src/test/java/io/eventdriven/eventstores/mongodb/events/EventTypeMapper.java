package io.eventdriven.eventstores.mongodb.events;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public interface EventTypeMapper {
  String toName(Class eventType);

  Optional<Class> toClass(String eventTypeName);

  EventTypeMapper DEFAULT = new EventTypeMapper() {
    private final Map<String, Optional<Class>> typeMap = new LinkedHashMap<>();
    private final Map<Class, String> typeNameMap = new LinkedHashMap<>();

    public String toName(Class eventType) {
      return typeNameMap.computeIfAbsent(
        eventType,
        c -> c.getTypeName()
      );
    }

    public Optional<Class> toClass(String eventTypeName) {
      return typeMap.computeIfAbsent(
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
  };

}


