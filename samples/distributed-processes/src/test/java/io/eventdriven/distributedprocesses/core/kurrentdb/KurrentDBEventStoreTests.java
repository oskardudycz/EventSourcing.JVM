package io.eventdriven.distributedprocesses.core.kurrentdb;

import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.KurrentDBConnectionString;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.testing.TestDatabases;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class KurrentDBEventStoreTests {
  private final KurrentDBEventStore eventStore = new KurrentDBEventStore(
    KurrentDBClient.create(KurrentDBConnectionString.parseOrThrow(TestDatabases.kurrentDBConnectionString()))
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

    assertThat(result).isInstanceOf(EventStore.AppendResult.Success.class);
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
