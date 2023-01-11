package io.eventdriven.distributedprocesses.core.collections;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class CollectionsExtensions {
  public static <T> T[] toArray(T... elements) {
    return elements;
  }

  public static <K, V> Map<K, V> mapWith(Map<K, V> map, K key, V value) {
    var clone = new HashMap<>(map);

    clone.replace(key, value);

    return clone;
  }

  public static <K, V> Map<K, V> mapWith(Map<K, V> map, Collection<K> keys, V value) {
    var clone = new HashMap<>(map);

    for (var key : keys) {
      clone.replace(key, value);
    }

    return clone;
  }

  public static <K, V> boolean valueIs(Map<K, V> map, K key, V... values) {
    var existingValue = map.get(key);

    return Arrays.stream(values)
      .filter(v -> v == existingValue)
      .findAny().isPresent();
  }
}
