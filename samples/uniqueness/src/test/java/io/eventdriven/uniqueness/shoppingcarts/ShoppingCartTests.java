package io.eventdriven.uniqueness.shoppingcarts;

import com.eventstore.dbclient.*;
import io.eventdriven.uniqueness.core.serialization.EventSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static io.eventdriven.uniqueness.shoppingcarts.ShoppingCartEvent.ShoppingCartOpened;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ShoppingCartTests {

  @Test
  public void EnforcesUniqueness_WithStreamId() throws ExecutionException, InterruptedException {
    var clientId = UUID.randomUUID();
    // We're assuming that there can be only a single shopping cart open for specific client.
    // We can enforce uniqueness by putting client id into a stream id
    var shoppingCartStreamId = "shopping_cart-%s".formatted(clientId);
    var shoppingCartOpened = new ShoppingCartOpened(clientId, clientId);

    // This one should succeed as we don't have such stream yet
    eventStore.appendToStream(
      shoppingCartStreamId,
      AppendToStreamOptions.get().expectedRevision(ExpectedRevision.NO_STREAM),
      EventSerializer.serialize(shoppingCartOpened)
    ).get();

    // This one will fail, as we're expecting that stream doesn't exist
    try {
      eventStore.appendToStream(
        shoppingCartStreamId,
        AppendToStreamOptions.get().expectedRevision(ExpectedRevision.NO_STREAM),
        EventSerializer.serialize(shoppingCartOpened)
      ).get();
    } catch (ExecutionException exception) {
      assertInstanceOf(WrongExpectedVersionException.class, exception.getCause());
    }
  }

  @Test
  public void sth() throws ExecutionException, InterruptedException {
    var clientId = UUID.randomUUID();
    // We're assuming that there can be only a single shopping cart open for specific client.
    // We can enforce uniqueness by putting client id into a stream id
    var shoppingCartStreamId = "shopping_cart-%s".formatted(clientId);
    var shoppingCartOpened = new ShoppingCartOpened(clientId, clientId);

    var metadata = new StreamMetadata();
    metadata.setMaxAge(1);

    eventStore.setStreamMetadata(shoppingCartStreamId, metadata);

    eventStore.appendToStream(
      shoppingCartStreamId,
      AppendToStreamOptions.get().expectedRevision(ExpectedRevision.NO_STREAM),
      EventSerializer.serialize(shoppingCartOpened),
      EventSerializer.serialize(shoppingCartOpened)
    ).get();

    Thread.sleep(2000);

    try {
      eventStore.appendToStream(
        shoppingCartStreamId,
        AppendToStreamOptions.get().expectedRevision(ExpectedRevision.NO_STREAM),
        EventSerializer.serialize(shoppingCartOpened)
      ).get();
    }catch (ExecutionException e){
      System.out.println(e);
    }
  }

  private EventStoreDBClient eventStore;

  @BeforeEach
  void beforeEach() throws ParseError {
    EventStoreDBClientSettings settings = EventStoreDBConnectionString.parse("esdb://localhost:2113?tls=false");
    this.eventStore = EventStoreDBClient.create(settings);
  }
}
