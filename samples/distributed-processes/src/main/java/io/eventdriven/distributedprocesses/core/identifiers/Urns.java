package io.eventdriven.distributedprocesses.core.identifiers;

import java.util.UUID;

public final class Urns {
  private static final String prefix = "urn";
  private static final String separator = ":";
  private static final int segmentCount = 4;

  public static String of(String namespace, String type, UUID tail) {
    return of(namespace, type, tail != null ? tail.toString() : null);
  }

  public static String of(String namespace, String type, String tail) {
    return String.join(
      separator,
      prefix,
      required(namespace, "namespace"),
      required(type, "type"),
      required(tail, "tail")
    );
  }

  public static String derive(String urn, String type) {
    return of(namespaceOf(urn), type, tailOf(urn));
  }

  public static String requireType(String urn, String type) {
    var actual = typeOf(urn);

    if (!actual.equals(type))
      throw new IllegalArgumentException(
        "Urn has to be of type '%s', but was '%s': '%s'".formatted(type, actual, urn)
      );

    return urn;
  }

  public static String namespaceOf(String urn) {
    return segmentsOf(urn)[1];
  }

  public static String typeOf(String urn) {
    return segmentsOf(urn)[2];
  }

  public static String tailOf(String urn) {
    return segmentsOf(urn)[3];
  }

  private static String[] segmentsOf(String urn) {
    if (urn == null || urn.isBlank())
      throw new IllegalArgumentException("Urn cannot be null or blank, but was: '%s'".formatted(urn));

    var segments = urn.split(separator, segmentCount);

    if (segments.length < segmentCount || !segments[0].equals(prefix))
      throw new IllegalArgumentException(
        "Urn has to be 'urn:<namespace>:<type>:<tail>', but was: '%s'".formatted(urn)
      );

    return segments;
  }

  private static String required(String segment, String name) {
    if (segment == null || segment.isBlank())
      throw new IllegalArgumentException(
        "Urn %s cannot be null or blank, but was: '%s'".formatted(name, segment)
      );

    return segment;
  }

  private Urns() {
  }
}
