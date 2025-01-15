package io.eventdriven.buildyourowneventstore.e01_storage.mongodb;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public interface EventTypeMapper {
  String toName(Class eventType);

  Optional<Class> toClass(String eventTypeName);

  EventTypeMapper DEFAULT = new EventTypeMapper() {
    private final Map<String, Optional<Class>> typeMap = new HashMap<>();
    private final Map<Class, String> typeNameMap = new HashMap<>();

    public String toName(Class eventType) {
      return.typeNameMap.computeIfAbsent(
        eventType,
        c -> c.getTypeName().replace("$", "__")
      );
    }

    public Optional<Class> toClass(String eventTypeName) {
      return typeMap.computeIfAbsent(
        eventTypeName,
        c -> {
          try {
            return Optional.of(Class.forName(eventTypeName.replace("__", "$")));
          } catch (ClassNotFoundException e) {
            return Optional.empty();
          }
        }
      );
    }
  };

}


