package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

public class DeliveryProviderClientTests {
  private static final Duration reservationValidity = Duration.ofHours(2);

  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(referenceId);
  private final ProductItem availableItem = new ProductItem(UUID.randomUUID(), 2);
  private final ProductItem unavailableItem = new ProductItem(UUID.randomUUID(), 1);
  private final ProductItem[] available = new ProductItem[]{availableItem};
  private final Function<ProductItem, Boolean> isProductAvailable =
    item -> Set.of(availableItem).contains(item);
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime reservedUntil = now.plus(reservationValidity);

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final MessageCatcher published = new MessageCatcher();

  @Test
  public void autoDeliveringProviderTurnsASentPackageIntoADeliveredOne() {
    configure(new AutoDeliveringDeliveryProvider(commandBus));

    commandBus.send(new ReserveStock(referenceId, available));
    published.reset();

    commandBus.send(new SendPackage(shipmentId));

    published.shouldReceiveMessages(
      new PackageWasSent(shipmentId, referenceId, available, now),
      new PackageWasDelivered(shipmentId, referenceId, now)
    );
  }

  @Test
  public void silentProviderLeavesThePackageSentAndUndelivered() {
    configure(new SilentDeliveryProvider());

    commandBus.send(new ReserveStock(referenceId, available));
    published.reset();

    commandBus.send(new SendPackage(shipmentId));

    published.shouldReceiveMessages(new PackageWasSent(shipmentId, referenceId, available, now));
  }

  @Test
  public void aReservationIsNeverHandedToTheProviderBeforeItIsSent() {
    var handedOver = new ArrayList<ShipmentId>();
    configure((handedShipmentId, productItems) -> handedOver.add(handedShipmentId));

    commandBus.send(new ReserveStock(referenceId, available));

    assertThat(handedOver).isEmpty();
  }

  @Test
  public void outOfStockShipmentIsNeverHandedToTheProvider() {
    var handedOver = new ArrayList<ShipmentId>();
    configure((handedShipmentId, productItems) -> handedOver.add(handedShipmentId));

    commandBus.send(new ReserveStock(referenceId, new ProductItem[]{unavailableItem}));

    assertThat(handedOver).isEmpty();
  }

  private void configure(DeliveryProvider deliveryProvider) {
    eventStore.use(published::catchMessage);

    ShipmentsConfig.configure(
      commandBus,
      eventStore,
      eventStore,
      new InMemoryEventBus(),
      deliveryProvider,
      isProductAvailable,
      reservationValidity,
      () -> now
    );
  }
}
