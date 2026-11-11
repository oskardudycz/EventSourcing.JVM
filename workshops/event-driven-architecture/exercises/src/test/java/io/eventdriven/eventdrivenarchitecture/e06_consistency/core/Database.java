package io.eventdriven.eventdrivenarchitecture.e06_consistency.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class Database {
  private static final JsonMapper mapper =
    JsonMapper.builder()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
      .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
      .build();

  private final Map<String, StoredDocument> storage;
  private final TransientFailures failures;
  private final List<Write> transactionWrites;
  private long lastPosition;

  public Database() {
    this(new LinkedHashMap<>(), new TransientFailures("Database not available!"), null);
  }

  private Database(Map<String, StoredDocument> storage, TransientFailures failures, List<Write> transactionWrites) {
    this.storage = storage;
    this.failures = failures;
    this.transactionWrites = transactionWrites;
  }

  public <T> Collection<T> collection(Class<T> typeClass) {
    return new Collection<>(typeClass, this);
  }

  // Changes made in the operations are committed together, or not at all if the operations or the commit fail
  public <Result> Result transaction(Function<Database, Result> operations) {
    var transaction = new Database(storage, null, new ArrayList<>());

    var result = operations.apply(transaction);

    failures.failIfUnavailable();
    commit(transaction.transactionWrites);

    return result;
  }

  public void transaction(Consumer<Database> operations) {
    transaction(transaction -> {
      operations.accept(transaction);
      return null;
    });
  }

  private void apply(Write write) {
    if (transactionWrites != null) {
      transactionWrites.add(write);
      return;
    }

    failures.failIfUnavailable();
    commit(List.of(write));
  }

  private void commit(List<Write> writes) {
    synchronized (storage) {
      for (var write : writes) {
        if (write.expectedVersion() == null)
          continue;

        var currentVersion = currentVersion(write.key());

        if (write.expectedVersion() != currentVersion)
          throw new ConcurrencyException(
            "Expected version %d of '%s', but it's %d".formatted(write.expectedVersion(), write.key(), currentVersion)
          );
      }

      for (var write : writes) {
        if (write.document() == null)
          storage.remove(write.key());
        else
          storage.put(write.key(), new StoredDocument(
            write.id(),
            write.type(),
            write.document(),
            currentVersion(write.key()) + 1,
            ++lastPosition
          ));
      }
    }
  }

  private int currentVersion(String key) {
    var stored = storage.get(key);
    return stored != null ? stored.version() : 0;
  }

  private record Write(String key, UUID id, Class<?> type, Object document, Integer expectedVersion) {
  }

  private record StoredDocument(UUID id, Class<?> type, Object document, int version, long position) {
  }

  public static class Collection<T> {
    private final Class<T> typeClass;
    private final Database database;

    public Collection(Class<T> typeClass, Database database) {
      this.typeClass = typeClass;
      this.database = database;
    }

    public void store(UUID id, Object obj) {
      database.apply(new Write(getId(id), id, typeClass, obj, null));
    }

    public void store(UUID id, Object obj, int expectedVersion) {
      database.apply(new Write(getId(id), id, typeClass, obj, expectedVersion));
    }

    public void delete(UUID id) {
      database.apply(new Write(getId(id), id, typeClass, null, null));
    }

    public Optional<Versioned<T>> get(UUID id) {
      Optional<Versioned<T>> result;

      synchronized (database.storage) {
        result = Optional.ofNullable(database.storage.get(getId(id)))
          // Clone to simulate getting new instance on loading
          .map(stored -> new Versioned<>(clone(stored.document()), stored.version()));
      }

      respondWithDelay();

      return result;
    }

    // Returns documents in the order they were stored, each store gets the next position
    public List<Stored<T>> getAll() {
      synchronized (database.storage) {
        return database.storage.values().stream()
          .filter(stored -> stored.type() == typeClass)
          .sorted(Comparator.comparingLong(StoredDocument::position))
          .map(stored -> new Stored<>(stored.id(), clone(stored.document()), stored.version(), stored.position()))
          .toList();
      }
    }

    private T clone(Object document) {
      try {
        return mapper.readValue(mapper.writeValueAsString(document), typeClass);
      } catch (JacksonException e) {
        throw new RuntimeException(e);
      }
    }

    // Respond with a delay, like a real database, so handlers running in parallel read before others store
    private static void respondWithDelay() {
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }

    private String getId(UUID id) {
      return "%s-%s".formatted(typeClass.getTypeName(), id);
    }
  }
}
