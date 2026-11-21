package io.eventdriven.eventdrivenarchitecture.e04_idempotency.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public class Database {
    private static final JsonMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();

    private record Stored(Object document, int version) {}

    private final Map<Class<?>, LinkedHashMap<UUID, Stored>> storage = new LinkedHashMap<>();
    private final Map<Class<?>, Map<UUID, Integer>> latestVersions = new HashMap<>();
    private final Map<Class<?>, Integer> typeChanges = new HashMap<>();

    public <T> Collection<T> collection(Class<T> typeClass) {
        return new Collection<>(typeClass, this);
    }

    public void transaction(Consumer<Database> operations) {
        var transaction = new Transaction(this);

        try {
            operations.accept(transaction);
        } catch (Exception e) {
            // Do nothing, discard transactional changes
            throw e;
        }

        transaction.commit();
    }

    synchronized Optional<Stored> read(Class<?> typeClass, UUID id) {
        return Optional.ofNullable(documents(typeClass).get(id));
    }

    synchronized Map<UUID, Stored> readAll(Class<?> typeClass) {
        return new LinkedHashMap<>(documents(typeClass));
    }

    synchronized void write(Class<?> typeClass, UUID id, Object document) {
        var newVersion = latestVersionOf(typeClass, id) + 1;
        latestVersions.computeIfAbsent(typeClass, _ -> new HashMap<>()).put(id, newVersion);
        typeChanges.merge(typeClass, 1, Integer::sum);
        documents(typeClass).put(id, new Stored(document, newVersion));
    }

    synchronized void remove(Class<?> typeClass, UUID id) {
        if (documents(typeClass).remove(id) == null) return;

        latestVersions.computeIfAbsent(typeClass, _ -> new HashMap<>()).put(id, latestVersionOf(typeClass, id) + 1);
        typeChanges.merge(typeClass, 1, Integer::sum);
    }

    private LinkedHashMap<UUID, Stored> documents(Class<?> typeClass) {
        return storage.computeIfAbsent(typeClass, _ -> new LinkedHashMap<>());
    }

    private synchronized int versionOf(Class<?> typeClass, UUID id) {
        var stored = documents(typeClass).get(id);
        return stored != null ? stored.version() : 0;
    }

    private synchronized int latestVersionOf(Class<?> typeClass, UUID id) {
        return latestVersions.getOrDefault(typeClass, Map.of()).getOrDefault(id, 0);
    }

    private synchronized int typeChangesOf(Class<?> typeClass) {
        return typeChanges.getOrDefault(typeClass, 0);
    }

    private static class Transaction extends Database {
        private record Key(Class<?> typeClass, UUID id) {}

        private record Change(Object document, boolean deleted) {}

        private final Database database;
        private final Map<Key, Integer> versionsSeen = new LinkedHashMap<>();
        private final Map<Class<?>, Integer> typeChangesSeen = new LinkedHashMap<>();
        private final Map<Key, Change> changes = new LinkedHashMap<>();

        private Transaction(Database database) {
            this.database = database;
        }

        @Override
        public void transaction(Consumer<Database> operations) {
            throw new UnsupportedOperationException("Nested transactions are not supported");
        }

        @Override
        Optional<Stored> read(Class<?> typeClass, UUID id) {
            var key = new Key(typeClass, id);
            var change = changes.get(key);

            if (change != null)
                return change.deleted()
                        ? Optional.empty()
                        : Optional.of(new Stored(change.document(), versionsSeen.get(key) + 1));

            synchronized (database) {
                versionsSeen.putIfAbsent(key, database.latestVersionOf(typeClass, id));
                return database.read(typeClass, id);
            }
        }

        @Override
        Map<UUID, Stored> readAll(Class<?> typeClass) {
            Map<UUID, Stored> documents;
            synchronized (database) {
                typeChangesSeen.putIfAbsent(typeClass, database.typeChangesOf(typeClass));
                documents = database.readAll(typeClass);
            }

            changes.forEach((key, change) -> {
                if (!key.typeClass().equals(typeClass)) return;

                if (change.deleted()) documents.remove(key.id());
                else documents.put(key.id(), new Stored(change.document(), versionsSeen.get(key) + 1));
            });
            return documents;
        }

        @Override
        void write(Class<?> typeClass, UUID id, Object document) {
            change(typeClass, id, new Change(document, false));
        }

        @Override
        void remove(Class<?> typeClass, UUID id) {
            change(typeClass, id, new Change(null, true));
        }

        private void change(Class<?> typeClass, UUID id, Change change) {
            var key = new Key(typeClass, id);
            versionsSeen.computeIfAbsent(key, _ -> database.latestVersionOf(typeClass, id));
            changes.put(key, change);
        }

        private void commit() {
            synchronized (database) {
                versionsSeen.forEach((key, versionSeen) -> {
                    if (database.latestVersionOf(key.typeClass(), key.id()) != versionSeen)
                        throw new ConcurrencyException("%s '%s' was changed by another transaction"
                                .formatted(key.typeClass().getSimpleName(), key.id()));
                });

                typeChangesSeen.forEach((typeClass, changesSeen) -> {
                    if (database.typeChangesOf(typeClass) != changesSeen)
                        throw new ConcurrencyException("%s documents were changed by another transaction"
                                .formatted(typeClass.getSimpleName()));
                });

                changes.forEach((key, change) -> {
                    if (change.deleted()) database.remove(key.typeClass(), key.id());
                    else database.write(key.typeClass(), key.id(), change.document());
                });
            }
        }
    }

    public static class Collection<T> {
        private final Class<T> typeClass;
        private final Database database;

        public Collection(Class<T> typeClass, Database database) {
            this.typeClass = typeClass;
            this.database = database;
        }

        public void store(UUID id, Object obj) {
            database.write(typeClass, id, obj);
        }

        public void delete(UUID id) {
            database.remove(typeClass, id);
        }

        public Optional<T> get(UUID id) {
            // Clone to simulate getting new instance on loading
            return database.read(typeClass, id).map(stored -> clone(stored.document()));
        }

        public List<T> findAll() {
            return database.readAll(typeClass).values().stream()
                    .map(stored -> clone(stored.document()))
                    .toList();
        }

        public void getAndUpdate(UUID id, Function<T, T> update) {
            try {
                var item = get(id).orElse(typeClass.getConstructor().newInstance());

                store(id, update.apply(item));
            } catch (InstantiationException
                    | IllegalAccessException
                    | InvocationTargetException
                    | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        private T clone(Object document) {
            try {
                return mapper.readValue(mapper.writeValueAsString(document), typeClass);
            } catch (JacksonException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
