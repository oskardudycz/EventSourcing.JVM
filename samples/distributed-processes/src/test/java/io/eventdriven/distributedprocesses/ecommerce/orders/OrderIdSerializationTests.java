package io.eventdriven.distributedprocesses.ecommerce.orders;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.OrderInitialized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OrderIdSerializationTests {
  // The same Jackson configuration InMemoryEventStore builds. Field visibility is set to ANY
  // there, which is what would out-rank @JsonValue and store a nested object instead of the urn.
  private static final ObjectMapper mapper = new JsonMapper()
    .registerModule(new JavaTimeModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
    .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

  private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
  private final OrderId orderId = OrderId.derivedFrom(cartId.value());
  private final UUID clientId = UUID.randomUUID();
  private final OffsetDateTime now = OffsetDateTime.parse("2026-09-16T10:00:00Z");

  private final OrderInitialized event = new OrderInitialized(
    orderId,
    cartId,
    clientId,
    new PricedProductItem[]{new PricedProductItem(UUID.randomUUID(), 2, 10)},
    20,
    now
  );

  @Test
  public void aTypedIdIsStoredAsABareUrnString() throws Exception {
    var json = mapper.writeValueAsString(event);

    assertThat(json).contains("\"orderId\":\"%s\"".formatted(orderId.value()));
    assertThat(json).doesNotContain("\"value\"");
  }

  @Test
  public void aStoredEventRoundTripsThroughTheEventStore() {
    var eventStore = new InMemoryEventStore();
    var streamId = OrderFacade.mapToStreamId(orderId);

    eventStore.append(streamId, event);

    var read = switch (eventStore.read(streamId)) {
      case EventStore.ReadResult.Success success -> success.events();
      default -> new Object[0];
    };

    assertThat(read).usingRecursiveFieldByFieldElementComparator().containsExactly(event);
    assertThat(((OrderInitialized) read[0]).orderId()).isEqualTo(orderId);
  }

  @Test
  public void anIdOfTheWrongTypeIsRejectedWhileReading() throws Exception {
    var json = mapper.writeValueAsString(event)
      .replace(orderId.value(), "urn:ecommerce:payment:%s".formatted(orderId.tail()));

    assertThatThrownBy(() -> mapper.readValue(json, OrderInitialized.class))
      .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void anIdCarryingAnotherEntitysTypeIsRejected() {
    assertThatThrownBy(() -> new OrderId("urn:ecommerce:payment:%s".formatted(UUID.randomUUID())))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("order");
  }

  @Test
  public void theStreamNameUsesTheTailNotTheWholeUrn() {
    assertThat(OrderFacade.mapToStreamId(orderId)).isEqualTo("Order-%s".formatted(orderId.tail()));
    assertThat(OrderFacade.mapToStreamId(orderId)).doesNotContain("urn:");
  }
}
