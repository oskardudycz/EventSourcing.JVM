package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class EventStore {
  private final Map<String, List<Consumer<Object>>> handlers = new ConcurrentHashMap<>();
  private final List<Consumer<Object>> middlewares = new ArrayList<>();
  private final Map<String, List<EventEnvelope>> events = new HashMap<>();

  public void appendToStream(String streamId, Object[] toAppend) {
    events.compute(streamId, (stream, events) -> {
      if (events == null)
        events = new ArrayList<>();

      events.addAll(
        Arrays.stream(toAppend)
          .map(e -> {
            try {
              return new EventEnvelope(e.getClass().getTypeName(), mapper.writeValueAsString(e));
            } catch (JsonProcessingException ex) {
              throw new RuntimeException(ex);
            }
          }).toList()
      );

      return events;
    });

    for (Object event : toAppend) {
      for (var middleware : middlewares)
        middleware.accept(event);

      var eventHandlers = handlers.get(event.getClass().getTypeName());

      if (eventHandlers != null)
        for (var handle : eventHandlers) {
          handle.accept(event);
        }
    }
  }

  public <State, Event> State aggregateStream(
    String streamId,
    BiFunction<State, Event, State> evolve,
    Supplier<State> getInitial
  ) {
    var events = readStream(streamId);

    var state = getInitial.get();

    for (var event : events) {
      state = evolve.apply(state, (Event) event);
    }

    return state;
  }

  public List<Object> readStream(String streamId) {
    return events.getOrDefault(streamId, new ArrayList<>()).stream().map(eventEnvelope -> {
      try {
        var eventClass = Class.forName(eventEnvelope.eventType);
        return (Object) mapper.readValue(eventEnvelope.json, eventClass);
      } catch (IOException | ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }).toList();
  }

  public <Event> EventStore subscribe(Class<Event> eventClass, Consumer<Event> handler) {
    handlers.compute(eventClass.getTypeName(), (eventType, consumers) -> {
      if (consumers == null)
        consumers = new ArrayList<>();

      consumers.add(
        event -> handler.accept(eventClass.cast(event))
      );

      return consumers;
    });

    return this;
  }

  public void use(Consumer<Object> middleware) {
    middlewares.add(middleware);
  }


  record EventEnvelope(String eventType, String json) {
  }

  private static final ObjectMapper mapper =
    new JsonMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
}
