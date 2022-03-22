package io.eventdriven.ecommerce.core.events;

import java.util.HashMap;

public final class EventTypeMapper {
  private static final EventTypeMapper Instance = new EventTypeMapper();

  private HashMap<String, Class> typeMap = new HashMap<>();
  private HashMap<Class, String> typeNameMap = new HashMap<>();

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
