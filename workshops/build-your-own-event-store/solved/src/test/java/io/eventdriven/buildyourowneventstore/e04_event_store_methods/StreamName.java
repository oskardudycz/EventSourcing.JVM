package io.eventdriven.buildyourowneventstore.e04_event_store_methods;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record StreamName(
  String streamType,
  String streamId
) {

  public static <StreamType> StreamName of(Class<StreamType> streamClass, String[] streamIdParts) {
    return new StreamName(toStreamType(streamClass), String.join(":", streamIdParts));
  }

  public static <StreamType> StreamName of(Class<StreamType> streamClass, String streamId) {
    return new StreamName(toStreamType(streamClass), streamId);
  }

  public static <StreamType> StreamName randomOf(Class<StreamType> streamClass) {
    return of(streamClass, UUID.randomUUID().toString());
  }

  private static <Stream> String toStreamType(Class<Stream> streamClass) {
    return streamClass.getCanonicalName().replace(".", "-");
  }

  @Override
  public @NotNull String toString() {
    return streamType + ":" + streamId;
  }
}
