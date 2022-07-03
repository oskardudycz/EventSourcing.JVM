package io.eventdriven.eventsversioning.transformations.esdb;

import com.eventstore.dbclient.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.eventdriven.eventsversioning.serialization.Serializer;
import io.eventdriven.eventsversioning.v1.ShoppingCartEvent;
import io.eventdriven.eventsversioning.v1.productitems.PricedProductItem;
import io.eventdriven.eventsversioning.v1.productitems.ProductItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class StreamTransformationsTests {
  public record ShoppingCartInitializedWithProducts(
    UUID shoppingCartId,
    UUID clientId,
    List<PricedProductItem> productItems
  ) {
  }

  public record EventMetadata(
    @JsonProperty("$correlationId")
    UUID correlationId
  ) {
  }

  public record EventData(
    String eventType,
    byte[] data,
    byte[] metaData
  ) {
  }

  public static List<EventData> flattenInitializedEventsWithProductItemsAdded(List<EventData> events) {
    var cartOpened = events.get(0);
    var cartInitializedCorrelationId =
      Serializer.deserialize(EventMetadata.class, cartOpened.metaData)
        .orElseThrow(() -> new RuntimeException("Error deserializing metadata"))
        .correlationId;

    var i = 1;
    var productItemsAdded = new ArrayList<EventData>();

    while (i < events.size()) {
      var eventData = events.get(i);

      if (!eventData.eventType().equals("product_item_added_v1"))
        break;

      var correlationId =
        Serializer.deserialize(EventMetadata.class, eventData.metaData)
          .orElseThrow(() -> new RuntimeException("Error deserializing metadata"))
          .correlationId;

      if (!correlationId.equals(cartInitializedCorrelationId))
        break;

      productItemsAdded.add(eventData);
      i++;
    }

    var mergedEvent = toShoppingCartInitializedWithProducts(
      cartOpened,
      productItemsAdded
    );

    return Stream.concat(
      Stream.of(mergedEvent),
      events.stream().skip(i)
    ).toList();
  }

  private static EventData toShoppingCartInitializedWithProducts(
    EventData shoppingCartInitialized,
    List<EventData> productItemsAdded
  ) {
    var shoppingCartInitializedJson =
      Serializer.deserialize(shoppingCartInitialized.data);

    var newEvent = new ShoppingCartInitializedWithProducts(
      UUID.fromString(shoppingCartInitializedJson.at("/shoppingCartId").asText()),
      UUID.fromString(shoppingCartInitializedJson.at("/clientId").asText()),
      productItemsAdded.stream()
        .map(pi -> {
          var pricedProductItem = Serializer.deserialize(pi.data);

          return new PricedProductItem(
            new ProductItem(
              UUID.fromString(pricedProductItem.at("/productItem/productItem/productId").asText()),
              pricedProductItem.at("/productItem/productItem/quantity").asInt()
            ),
            pricedProductItem.at("/productItem/unitPrice").asDouble()
          );
        }).toList()
    );

    return new EventData(
      "shopping_cart_opened_v2",
      Serializer.serialize(newEvent),
      shoppingCartInitialized.metaData
    );
  }

  public class StreamTransformations {
    private final List<Function<List<EventData>, List<EventData>>> jsonTransformations = new ArrayList<>();

    public List<EventData> transform(List<EventData> events) {
      if (jsonTransformations.isEmpty())
        return events;

      var result = events;

      for (var transform : jsonTransformations) {
        result = transform.apply(result);
      }
      return result;
    }

    public StreamTransformations register(Function<List<EventData>, List<EventData>> transformJson) {
      jsonTransformations.add(transformJson);
      return this;
    }
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
    private final Map<String, Class> typeMap = new HashMap<>();
    private final Map<Class, String> typeNameMap = new HashMap<>();

    public <Event> EventTypeMapping register(Class<Event> eventClass, String... typeNames) {
      for (var typeName : typeNames) {
        typeMap.put(typeName, eventClass);
        typeNameMap.put(eventClass, typeName);
      }

      return this;
    }

    public Class map(String eventType) {
      return typeMap.get(eventType);
    }

    public String map(Class eventClass) {
      return typeNameMap.get(eventClass);
    }
  }

  public record EventSerializer(
    EventTypeMapping mapping,
    StreamTransformations streamTransformations,
    EventTransformations transformations
  ) {
    public Optional<Object> deserialize(String eventTypeName, byte[] json) {
      return transformations.tryTransform(eventTypeName, json)
        .or(() -> Serializer.deserialize(mapping().map(eventTypeName), json));
    }

    public List<com.eventstore.dbclient.EventData> serialize(EventEnvelope... events) {
      return Arrays.stream(events)
        .map(eventEnvelope ->
          new com.eventstore.dbclient.EventData(
            UUID.randomUUID(),
            mapping.map(eventEnvelope.data().getClass()),
            "application/json",
            Serializer.serialize(eventEnvelope.data()),
            Serializer.serialize(eventEnvelope.metadata())
          )
        )
        .toList();
    }

    public List<Optional<Object>> deserialize(ResolvedEvent... resolvedEvents) {
      var events = Arrays.stream(resolvedEvents)
        .map(resolvedEvent -> new EventData(
            resolvedEvent.getEvent().getEventType(),
            resolvedEvent.getEvent().getEventData(),
            resolvedEvent.getEvent().getUserMetadata()
          )
        ).toList();

      return streamTransformations.transform(events).stream()
        .map(event -> deserialize(event.eventType, event.data))
        .toList();
    }
  }

  public record EventEnvelope(
    Object data,
    EventMetadata metadata
  ) {
  }

  @Test
  public void UpcastObjects_Should_BeForwardCompatible() throws ExecutionException, InterruptedException {
    // Given
    var mapping = new EventTypeMapping()
      .register(ShoppingCartEvent.ShoppingCartOpened.class, "shopping_cart_opened_v2")
      .register(ShoppingCartInitializedWithProducts.class, "shopping_cart_opened_v2")
      .register(ShoppingCartEvent.ProductItemAddedToShoppingCart.class, "product_item_added_v1")
      .register(ShoppingCartEvent.ShoppingCartConfirmed.class, "shopping_card_confirmed_v1");

    var streamTransformations =
      new StreamTransformations()
        .register(StreamTransformationsTests::flattenInitializedEventsWithProductItemsAdded);

    var serializer = new EventSerializer(
      mapping,
      streamTransformations,
      new EventTransformations()
    );

    var shoppingCardId = UUID.randomUUID();
    var clientId = UUID.randomUUID();
    var theSameCorrelationId = UUID.randomUUID();
    var productItem = new PricedProductItem(new ProductItem(UUID.randomUUID(), 1), 23.22);

    var events = new EventEnvelope[]{
      new EventEnvelope(
        new ShoppingCartEvent.ShoppingCartOpened(shoppingCardId, clientId),
        new EventMetadata(theSameCorrelationId)
      ),
      new EventEnvelope(
        new ShoppingCartEvent.ProductItemAddedToShoppingCart(shoppingCardId, productItem),
        new EventMetadata(theSameCorrelationId)
      ),
      new EventEnvelope(
        new ShoppingCartEvent.ProductItemAddedToShoppingCart(shoppingCardId, productItem),
        new EventMetadata(theSameCorrelationId)
      ),
      new EventEnvelope(
        new ShoppingCartEvent.ProductItemAddedToShoppingCart(shoppingCardId, productItem),
        new EventMetadata(UUID.randomUUID())
      ),
      new EventEnvelope(
        new ShoppingCartEvent.ShoppingCartConfirmed(shoppingCardId, LocalDateTime.now()),
        new EventMetadata(UUID.randomUUID())
      )
    };

    var serialisedEvents = serializer.serialize(events);

    this.eventStore.appendToStream("shopping_cart-%s".formatted(shoppingCardId), serialisedEvents.iterator()).get();

    // When
    var readEvents =
      this.eventStore.readStream("shopping_cart-%s".formatted(shoppingCardId)).get()
        .getEvents()
        .toArray(ResolvedEvent[]::new);

    var deserializedEvents = serializer.deserialize(readEvents).stream()
      .filter(Optional::isPresent)
      .map(Optional::get)
      .toList();

    // Then
    assertEquals(3, deserializedEvents.size());

    assertInstanceOf(ShoppingCartInitializedWithProducts.class, deserializedEvents.get(0));
    var shoppingCartInitializedWithProducts = (ShoppingCartInitializedWithProducts) deserializedEvents.get(0);

    assertEquals(shoppingCardId, shoppingCartInitializedWithProducts.shoppingCartId());
    assertEquals(clientId, shoppingCartInitializedWithProducts.clientId());
    assertEquals(2, shoppingCartInitializedWithProducts.productItems().size());
    assertEquals(productItem, shoppingCartInitializedWithProducts.productItems().get(0));
    assertEquals(productItem, shoppingCartInitializedWithProducts.productItems().get(1));


    assertInstanceOf(ShoppingCartEvent.ProductItemAddedToShoppingCart.class, deserializedEvents.get(1));
    var productItemAddedToShoppingCart = (ShoppingCartEvent.ProductItemAddedToShoppingCart) deserializedEvents.get(1);

    assertEquals(shoppingCardId, productItemAddedToShoppingCart.shoppingCartId());
    assertEquals(productItem, productItemAddedToShoppingCart.productItem());


    assertInstanceOf(ShoppingCartEvent.ShoppingCartConfirmed.class, deserializedEvents.get(2));
    var shoppingCartConfirmed = (ShoppingCartEvent.ShoppingCartConfirmed) deserializedEvents.get(2);

    assertEquals(shoppingCardId, shoppingCartConfirmed.shoppingCartId());
  }

  private EventStoreDBClient eventStore;

  @BeforeEach
  void beforeEach() throws ParseError {
    EventStoreDBClientSettings settings = EventStoreDBConnectionString.parse("esdb://localhost:2113?tls=false");
    this.eventStore = EventStoreDBClient.create(settings);
  }
}
