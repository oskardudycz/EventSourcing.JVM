package io.eventdriven.testing;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.joining;

// A record's toString prints an array component as an identity hash, which would make the
// transcript of a message carrying one unreadable. Render records component by component instead.
final class Transcript {
  private Transcript() {
  }

  static String numbered(List<?> messages) {
    if (messages.isEmpty())
      return "  <none>";

    var lines = new ArrayList<String>();

    for (var i = 0; i < messages.size(); i++) {
      lines.add("  %d. %s".formatted(i + 1, describe(messages.get(i))));
    }

    return String.join(System.lineSeparator(), lines);
  }

  private static String describe(Object message) {
    if (message == null)
      return "null";

    if (message.getClass().isArray())
      return describeArray(message);

    if (message instanceof Collection<?> collection)
      return collection.stream().map(Transcript::describe).collect(joining(", ", "[", "]"));

    if (message instanceof Record record)
      return describeRecord(record);

    return message.toString();
  }

  private static String describeArray(Object array) {
    var items = new ArrayList<String>();

    for (var i = 0; i < Array.getLength(array); i++) {
      items.add(describe(Array.get(array, i)));
    }

    return "[%s]".formatted(String.join(", ", items));
  }

  private static String describeRecord(Record record) {
    var components = new ArrayList<String>();

    try {
      for (var component : record.getClass().getRecordComponents()) {
        components.add(
          "%s=%s".formatted(component.getName(), describe(component.getAccessor().invoke(record)))
        );
      }
    } catch (ReflectiveOperationException accessorNotReachable) {
      return record.toString();
    }

    return "%s[%s]".formatted(record.getClass().getSimpleName(), String.join(", ", components));
  }
}
