package io.eventdriven.introductiontoeventsourcing.solved.e06_business_logic.esdb.tools;

import com.eventstore.dbclient.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class EventStoreDBTest {
  protected EventStoreDBClient eventStore;

  protected static final ObjectMapper mapper =
    new JsonMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);;

  @BeforeEach
  void beforeEach() throws ParseError {
    EventStoreDBClientSettings settings = EventStoreDBConnectionString.parse("esdb://localhost:2113?tls=false");
    eventStore = EventStoreDBClient.create(settings);
  }

  protected CompletableFuture<WriteResult> appendEvents(EventStoreDBClient eventStore, String streamName, Object[] events) {
    // 1. Add logic here
    return eventStore.appendToStream(
      streamName,
      Arrays.stream(events)
        .map(this::serialize)
        .iterator()
    );
  }

  private EventData serialize(Object event) {
    try {
      return EventDataBuilder.json(
        UUID.randomUUID(),
        event.getClass().getTypeName(),
        mapper.writeValueAsBytes(event)
      ).build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
