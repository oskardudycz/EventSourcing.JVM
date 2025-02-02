package io.eventdriven.eventstores;

public final class StreamType {
  public static <Stream> String of(Class<Stream> streamClass) {
    return streamClass.getCanonicalName().replace(".", "-");
  }
}
