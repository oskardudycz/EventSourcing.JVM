package io.eventdriven.eventstores;


import java.util.UUID;

public record StreamName(
  String streamType,
  String streamId
) {

  public static <Type> StreamName of(Class<Type> streamClass, String[] streamIdParts) {
    return new StreamName(StreamType.of(streamClass), String.join(":", streamIdParts));
  }

  public static <Type> StreamName of(Class<Type> streamClass, String streamId) {
    return new StreamName(StreamType.of(streamClass), streamId);
  }

  public static <Type> StreamName randomOf(Class<Type> streamClass) {
    return of(streamClass, UUID.randomUUID().toString());
  }

  @Override
  public String toString() {
    return streamType + ":" + streamId;
  }
}
