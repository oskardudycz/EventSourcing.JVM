package io.eventdriven.ecommerce.core.events;

public record EventMetadata(
  String streamName,
  String eventId,
  long streamPosition,
  long logPosition,
  String eventType
) {
  public String streamId(){
    return streamName.substring(streamName.indexOf("-") + 1);
  }
}
