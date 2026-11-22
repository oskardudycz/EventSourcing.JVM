package io.eventdriven.core.messaging.versioning;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MessageTypeMapper {
    private final Set<String> trustedPackages = new LinkedHashSet<>();
    private final Map<String, Class<?>> classesByExplicitName = new HashMap<>();
    private final Map<Class<?>, String> explicitNamesByClass = new HashMap<>();
    private final Map<String, MessageMapping.OldName> oldNames = new HashMap<>();

    public MessageTypeMapper(List<MessageMapping> mappings) {
        for (var mapping : mappings) {
            trustedPackages.addAll(mapping.trustedPackages());
            mapping.explicitNames().forEach(this::addExplicitName);
            mapping.oldNames().forEach(oldName -> oldNames.put(oldName.typeName(), oldName));
        }
    }

    public String nameOf(Class<?> messageClass) {
        return explicitNamesByClass.getOrDefault(messageClass, messageClass.getName());
    }

    public Set<String> namesOf(Class<?> messageClass) {
        var names = new LinkedHashSet<String>();
        names.add(nameOf(messageClass));
        oldNames.values().stream()
                .filter(oldName -> oldName.currentClass().equals(messageClass))
                .forEach(oldName -> names.add(oldName.typeName()));
        return names;
    }

    public Optional<Class<?>> classOf(String typeName) {
        if (classesByExplicitName.containsKey(typeName)) return Optional.of(classesByExplicitName.get(typeName));

        if (oldNames.containsKey(typeName))
            return Optional.of(oldNames.get(typeName).currentClass());

        return trustedClassOf(typeName);
    }

    public Optional<MessageTransformer> transformerOf(String typeName) {
        return Optional.ofNullable(oldNames.get(typeName)).map(MessageMapping.OldName::transformer);
    }

    public Map<String, Class<?>> explicitNames() {
        return Map.copyOf(classesByExplicitName);
    }

    public String[] trustedPackages() {
        return trustedPackages.toArray(String[]::new);
    }

    private void addExplicitName(String typeName, Class<?> messageClass) {
        var existing = classesByExplicitName.putIfAbsent(typeName, messageClass);

        if (existing != null && !existing.equals(messageClass))
            throw new IllegalStateException("The type name '%s' is mapped to both %s and %s"
                    .formatted(typeName, existing.getName(), messageClass.getName()));

        explicitNamesByClass.put(messageClass, typeName);
    }

    private Optional<Class<?>> trustedClassOf(String typeName) {
        var lastDot = typeName.lastIndexOf('.');

        if (lastDot < 0 || !trustedPackages.contains(typeName.substring(0, lastDot))) return Optional.empty();

        try {
            return Optional.of(Class.forName(typeName, false, getClass().getClassLoader()));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }
}
