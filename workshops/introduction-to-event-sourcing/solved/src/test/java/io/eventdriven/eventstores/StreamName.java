package io.eventdriven.eventstores;


import java.util.UUID;

public record StreamName(
  String streamType,
  String streamId
) {
  public static StreamName of(String streamName) {
    var separatorIndex = streamName.indexOf('-');

    var streamType = separatorIndex != -1 && separatorIndex != streamName.length() - 1 ?
      streamName.substring(0, separatorIndex)
      : "unset";

    var streamId = separatorIndex != -1 && separatorIndex != streamName.length() - 1 ?
      streamName.substring(separatorIndex)
      : streamName;

    return new StreamName(streamType, streamId);
  }

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
