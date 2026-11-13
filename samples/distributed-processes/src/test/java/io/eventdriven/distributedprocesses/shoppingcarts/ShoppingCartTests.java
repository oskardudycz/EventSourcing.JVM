package io.eventdriven.distributedprocesses.shoppingcarts;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;

import com.eventstore.dbclient.*;
import io.eventdriven.distributedprocesses.core.serialization.EventSerializer;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ShoppingCartTests {

  @Test
  public void enforcesUniqueness_WithStreamId() throws ExecutionException, InterruptedException {
    var clientId = UUID.randomUUID();
    // We're assuming that there can be only a single shopping cart open for specific client.
    // We can enforce uniqueness by putting client id into a stream id
    var shoppingCartStreamId = "shopping_cart-%s".formatted(clientId);
    var productItemAdded = new ShoppingCartEvent.ProductItemAddedToShoppingCart(
      ShoppingCartId.of(clientId, 1),
      clientId,
      new PricedProductItem(new ProductItem(UUID.randomUUID(), 1), 10),
      OffsetDateTime.now()
    );

    // This one should succeed as we don't have such stream yet
    eventStore.appendToStream(
      shoppingCartStreamId,
      AppendToStreamOptions.get().expectedRevision(ExpectedRevision.noStream()),
      EventSerializer.serialize(productItemAdded)
    ).get();

    // This one will fail, as we're expecting that stream doesn't exist
    try {
      eventStore.appendToStream(
        shoppingCartStreamId,
        AppendToStreamOptions.get().expectedRevision(ExpectedRevision.noStream()),
        EventSerializer.serialize(productItemAdded)
      ).get();
    } catch (ExecutionException exception) {
      assertInstanceOf(WrongExpectedVersionException.class, exception.getCause());
    }
  }

  private EventStoreDBClient eventStore;

  @BeforeEach
  void beforeEach() throws ConnectionStringParsingException {
    EventStoreDBClientSettings settings = EventStoreDBConnectionString.parseOrThrow("esdb://localhost:2113?tls=false");
    this.eventStore = EventStoreDBClient.create(settings);
  }
}
