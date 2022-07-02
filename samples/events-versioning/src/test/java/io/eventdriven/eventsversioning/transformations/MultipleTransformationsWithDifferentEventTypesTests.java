package io.eventdriven.eventsversioning.transformations;

import com.fasterxml.jackson.databind.JsonNode;
import io.eventdriven.eventsversioning.serialization.Serializer;
import io.eventdriven.eventsversioning.v1.ShoppingCartEvent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.*;

public class MultipleTransformationsWithDifferentEventTypesTests {
  public record Client(
    UUID id,
    String name
  ) {
    public Client {
      if (name == null) {
        name = "Unknown";
      }
    }
  }

  public record ShoppingCartOpened(
    UUID shoppingCartId,
    Client client
  ) {
  }

  public enum ShoppingCartStatus {
    Pending,
    Opened,
    Confirmed,
    Cancelled
  }

  public record ShoppingCartInitializedWithStatus(
    UUID shoppingCartId,
    Client client,
    // Adding new not required property as nullable
    ShoppingCartStatus status
  ) {
  }

  public static ShoppingCartInitializedWithStatus upcastV1(
    JsonNode oldEvent
  ) {
    return new ShoppingCartInitializedWithStatus(
      UUID.fromString(oldEvent.at("/shoppingCartId").asText()),
      new Client(
        UUID.fromString(oldEvent.at("/clientId").asText()),
        null
      ),
      ShoppingCartStatus.Opened
    );
  }

  public static ShoppingCartInitializedWithStatus upcastV2(
    ShoppingCartOpened oldEvent
  ) {
    return new ShoppingCartInitializedWithStatus(
      oldEvent.shoppingCartId(),
      oldEvent.client(),
      ShoppingCartStatus.Opened
    );
  }

  public class EventTransformations {
    private final Map<String, Function<byte[], Object>> jsonTransformations = new HashMap<>();

    public Optional<Object> tryTransform(String eventTypeName, byte[] json) {
      if (!jsonTransformations.containsKey(eventTypeName)) {
        return Optional.empty();
      }

      var transformJson = jsonTransformations.get(eventTypeName);

      return Optional.of(transformJson.apply(json));
    }

    public <Event> EventTransformations register(
      String eventTypeName,
      Function<JsonNode, Event> transformJson
    ) {
      jsonTransformations.put(
        eventTypeName,
        json -> transformJson.apply(Serializer.deserialize(json))
      );
      return this;
    }

    public <OldEvent, Event> EventTransformations register(
      Class<OldEvent> oldEventClass,
      String eventTypeName,
      Function<OldEvent, Event> transformEvent
    ) {
      jsonTransformations.put(
        eventTypeName,
        json -> transformEvent.apply(Serializer.deserialize(oldEventClass, json)
          .orElseThrow(() -> new RuntimeException("Error deserializing")))
      );
      return this;
    }
  }

  public class EventTypeMapping {
    private final Map<String, Class> mappings = new HashMap<>();

    public <Event> EventTypeMapping register(Class<Event> eventClass, String... typeNames) {

      for (var typeName : typeNames) {
        mappings.put(typeName, eventClass);
      }

      return this;
    }

    public Class map(String eventType) {
      return mappings.get(eventType);
    }
  }

  public record EventSerializer(
    EventTypeMapping mapping,
    EventTransformations transformations
  ) {
    public Optional<Object> deserialize(String eventTypeName, byte[] json) {
      return transformations.tryTransform(eventTypeName, json)
        .or(() -> Serializer.deserialize(mapping().map(eventTypeName), json));
    }
  }

  @Test
  public void UpcastObjects_Should_BeForwardCompatible() {
    // Given
    final String eventTypeV1Name = "shopping_cart_initialized_v1";
    final String eventTypeV2Name = "shopping_cart_initialized_v2";
    final String eventTypeV3Name = "shopping_cart_initialized_v3";

    var mapping = new EventTypeMapping()
      .register(ShoppingCartInitializedWithStatus.class,
        eventTypeV1Name,
        eventTypeV2Name,
        eventTypeV3Name
      );

    var transformations = new EventTransformations()
      .register(eventTypeV1Name, MultipleTransformationsWithDifferentEventTypesTests::upcastV1)
      .register(ShoppingCartOpened.class, eventTypeV2Name, MultipleTransformationsWithDifferentEventTypesTests::upcastV2);

    var serializer = new EventSerializer(mapping, transformations);

    var eventV1 = new ShoppingCartEvent.ShoppingCartOpened(
      UUID.randomUUID(),
      UUID.randomUUID()
    );
    var eventV2 = new ShoppingCartOpened(
      UUID.randomUUID(),
      new Client(UUID.randomUUID(), "Oscar the Grouch")
    );
    var eventV3 = new ShoppingCartInitializedWithStatus(
      UUID.randomUUID(),
      new Client(UUID.randomUUID(), "Big Bird"),
      ShoppingCartStatus.Pending
    );

    var events = new HashMap<String, byte[]>() {{
      put(eventTypeV1Name, Serializer.serialize(eventV1));
      put(eventTypeV2Name, Serializer.serialize(eventV2));
      put(eventTypeV3Name, Serializer.serialize(eventV3));
    }};

    // When
    var deserializedEvents = events.entrySet().stream()
      .map(event -> serializer.deserialize(event.getKey(), event.getValue()))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .filter(ShoppingCartInitializedWithStatus.class::isInstance)
      .map(ShoppingCartInitializedWithStatus.class::cast)
      .collect(toMap(ShoppingCartInitializedWithStatus::shoppingCartId, event -> event));

    // Then
    assertEquals(3, deserializedEvents.size());

    assertTrue(deserializedEvents.containsKey(eventV1.shoppingCartId()));
    var upcastedV1Event = deserializedEvents.get(eventV1.shoppingCartId());

    assertEquals(eventV1.shoppingCartId(), upcastedV1Event.shoppingCartId());
    assertEquals(eventV1.clientId(), upcastedV1Event.client().id());
    assertEquals("Unknown", upcastedV1Event.client().name());
    assertEquals(ShoppingCartStatus.Opened, upcastedV1Event.status());

    assertTrue(deserializedEvents.containsKey(eventV2.shoppingCartId()));
    var upcastedV2Event = deserializedEvents.get(eventV2.shoppingCartId());

    assertEquals(eventV2.shoppingCartId(), upcastedV2Event.shoppingCartId());
    assertEquals(eventV2.client(), upcastedV2Event.client());
    assertEquals(ShoppingCartStatus.Opened, upcastedV2Event.status());

    assertTrue(deserializedEvents.containsKey(eventV3.shoppingCartId()));
    assertEquals(eventV3, deserializedEvents.get(eventV3.shoppingCartId()));
  }
}
