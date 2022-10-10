package io.eventdriven.buildyourowneventstore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class EventTypeMapper {
    private static final EventTypeMapper Instance = new EventTypeMapper();

    private final Map<String, Optional<Class>> typeMap = new HashMap<>();
    private final Map<Class, String> typeNameMap = new HashMap<>();

    public static String toName(Class eventType) {
        return Instance.typeNameMap.computeIfAbsent(
            eventType,
            c -> c.getTypeName().replace(".", "_")
        );
    }

    public static Optional<Class> toClass(String eventTypeName) {
        return Instance.typeMap.computeIfAbsent(
            eventTypeName,
            c -> {
                try {
                    return Optional.of(Class.forName(eventTypeName.replace("_", ".")));
                } catch (ClassNotFoundException e) {
                    return Optional.empty();
                }
            }
        );
    }
}

