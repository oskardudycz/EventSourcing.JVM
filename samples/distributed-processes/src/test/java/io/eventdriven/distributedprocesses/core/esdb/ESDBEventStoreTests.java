package io.eventdriven.distributedprocesses.core.esdb;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Needs EventStoreDB: docker compose up -d eventstore.db
public class ESDBEventStoreTests {
  private final ESDBEventStore eventStore = new ESDBEventStore(
    EventStoreDBClient.create(EventStoreDBConnectionString.parseOrThrow("esdb://localhost:2113?tls=false"))
  );

  private final String cartId = UUID.randomUUID().toString();
  private final String streamId = "ShoppingCart-%s".formatted(cartId);

  @Test
  public void subscriberGetsAppendedEventsOfItsTypeWithTheirStreamAndLogPositions() {
    var received = new CopyOnWriteArrayList<EventEnvelope<ProductItemAdded>>();
    eventStore.subscribe(ProductItemAdded.class, envelope -> {
      if (envelope.metadata().streamId().equals(streamId))
        received.add(envelope);
    });

    var result = eventStore.append(
      streamId,
      new ShoppingCartOpened(cartId),
      new ProductItemAdded(cartId, "t-shirt")
    );

    var logPosition = ((EventStore.AppendResult.Success) result).logPosition().getCommitUnsigned();

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
      assertThat(received).containsExactly(
        new EventEnvelope<>(new ProductItemAdded(cartId, "t-shirt"), new EventMetadata(streamId, 1, logPosition))
      )
    );
  }

  record ShoppingCartOpened(String cartId) {
  }

  record ProductItemAdded(String cartId, String productId) {
  }
}
