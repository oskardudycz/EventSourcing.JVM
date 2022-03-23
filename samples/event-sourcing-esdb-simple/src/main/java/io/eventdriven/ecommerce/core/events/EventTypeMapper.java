package io.eventdriven.ecommerce.core.events;

import java.util.HashMap;
import java.util.Map;

public final class EventTypeMapper {
  private static final EventTypeMapper Instance = new EventTypeMapper();

  private Map<String, Class> typeMap = new HashMap<>();
  private Map<Class, String> typeNameMap = new HashMap<>();

  public static String toName(Class eventType) {
    return Instance.typeNameMap.computeIfAbsent(
      eventType,
      c -> c.getTypeName().replace(".", "_")
    );
  }

  public static Class toClass(String eventTypeName) {
    return Instance.typeMap.computeIfAbsent(
      eventTypeName,
      c -> {
        try {
          return Class.forName(eventTypeName.replace("_", "."));
        } catch (ClassNotFoundException e) {
            return null;
        }
      }
    );
  }
}
