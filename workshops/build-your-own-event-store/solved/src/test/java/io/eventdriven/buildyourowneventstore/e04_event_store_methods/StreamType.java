package io.eventdriven.buildyourowneventstore.e04_event_store_methods;

public final class StreamType {
  public static <Stream> String of(Class<Stream> streamClass) {
    return streamClass.getCanonicalName().replace(".", "-");
  }
}
