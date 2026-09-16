package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class ReservationExpiryWorkerTests {
  private static final Duration reservationValidity = Duration.ofHours(2);

  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(referenceId);
  private final ProductItem availableItem = new ProductItem(UUID.randomUUID(), 2);
  private final ProductItem[] available = new ProductItem[]{availableItem};
  private final Function<ProductItem, Boolean> isProductAvailable =
    item -> Set.of(availableItem).contains(item);
  private final OffsetDateTime reservedAt = OffsetDateTime.now();
  private final OffsetDateTime reservedUntil = reservedAt.plus(reservationValidity);
  private final OffsetDateTime beforeExpiry = reservedUntil.minusMinutes(1);
  private final OffsetDateTime afterExpiry = reservedUntil.plusMinutes(1);

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final MessageCatcher published = new MessageCatcher();

  private final ReservationExpiryWorker worker;

  public ReservationExpiryWorkerTests() {
    eventStore.use(published::catchMessage);
    worker = ShipmentsConfig.configure(
      commandBus,
      eventStore,
      eventStore,
      new InMemoryEventBus(),
      new SilentDeliveryProvider(),
      isProductAvailable,
      reservationValidity,
      () -> reservedAt
    ).reservationExpiryWorker();
  }

  @Test
  public void reservationThatIsNeverSentExpiresOnceItsDeadlinePasses() {
    reserve();

    worker.run(afterExpiry);

    published.shouldReceiveMessages(
      new StockReservationExpired(shipmentId, referenceId, afterExpiry)
    );
  }

  @Test
  public void runningBeforeTheDeadlineSendsNothing() {
    reserve();

    worker.run(beforeExpiry);

    published.shouldNotReceiveAnyEvent();
  }

  @Test
  public void reservationOfASentPackageNeverExpires() {
    reserve();
    commandBus.send(new SendPackage(shipmentId));
    published.reset();

    worker.run(afterExpiry);

    published.shouldNotReceiveAnyEvent();
  }

  @Test
  public void releasedReservationNeverExpires() {
    reserve();
    commandBus.send(new ReleaseStock(shipmentId));
    published.reset();

    worker.run(afterExpiry);

    published.shouldNotReceiveAnyEvent();
  }

  @Test
  public void runningTwiceExpiresTheSameReservationOnlyOnce() {
    reserve();

    worker.run(afterExpiry);
    assertThatCode(() -> worker.run(afterExpiry.plusMinutes(1))).doesNotThrowAnyException();

    assertThat(published.published)
      .containsOnlyOnce(new StockReservationExpired(shipmentId, referenceId, afterExpiry));
    assertThat(published.published).hasSize(1);
  }

  private void reserve() {
    commandBus.send(new ReserveStock(referenceId, available));
    published.reset();
  }
}
