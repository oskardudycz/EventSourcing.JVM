package io.eventdriven.distributedprocesses.ecommerce.shipments.delivery;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
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
import static org.assertj.core.api.Assertions.assertThatCode;

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
  public void callingBackProviderDeliversTheParcelItBooked() {
    var deliveryProvider = new CallingBackDeliveryProvider(commandBus);
    configure(deliveryProvider);

    commandBus.send(new ReserveStock(referenceId, available));
    published.reset();

    commandBus.send(new SendPackage(shipmentId));
    deliveryProvider.deliverBookedParcels();

    published.shouldReceiveMessages(
      new PackageWasSent(
        shipmentId,
        referenceId,
        available,
        CallingBackDeliveryProvider.booking.carrier(),
        CallingBackDeliveryProvider.booking.trackingNumber(),
        now
      ),
      new PackageWasDelivered(shipmentId, referenceId, now)
    );
  }

  @Test
  public void silentProviderLeavesThePackageSentAndUndelivered() {
    configure(new SilentDeliveryProvider());

    commandBus.send(new ReserveStock(referenceId, available));
    published.reset();

    commandBus.send(new SendPackage(shipmentId));

    published.shouldReceiveMessages(new PackageWasSent(
      shipmentId,
      referenceId,
      available,
      SilentDeliveryProvider.booking.carrier(),
      SilentDeliveryProvider.booking.trackingNumber(),
      now
    ));
  }

  @Test
  public void unreachableProviderLeavesTheShipmentReservedInsteadOfThrowing() {
    configure(new ThrowingDeliveryProvider());

    commandBus.send(new ReserveStock(referenceId, available));
    published.reset();

    assertThatCode(() -> commandBus.send(new SendPackage(shipmentId)))
      .doesNotThrowAnyException();

    published.shouldNotReceiveAnyEvent();

    // Still reserved, so the stock can be released
    commandBus.send(new ReleaseStock(shipmentId));

    published.shouldReceiveMessages(new StockReleased(shipmentId, referenceId, now));
  }

  @Test
  public void providerIsAskedWithTheShipmentAndItsReservedItems() {
    var bookings = new ArrayList<String>();
    configure((bookedShipmentId, productItems) -> {
      bookings.add("%s:%s".formatted(bookedShipmentId, productItems.length));
      return SilentDeliveryProvider.booking;
    });

    commandBus.send(new ReserveStock(referenceId, available));
    commandBus.send(new SendPackage(shipmentId));

    assertThat(bookings).containsExactly("%s:%s".formatted(shipmentId, available.length));
  }

  @Test
  public void sendingTheSamePackageTwiceBooksOneParcel() {
    var booked = new ArrayList<ShipmentId>();
    configure((bookedShipmentId, productItems) -> {
      booked.add(bookedShipmentId);
      return SilentDeliveryProvider.booking;
    });

    commandBus.send(new ReserveStock(referenceId, available));
    commandBus.send(new SendPackage(shipmentId), new SendPackage(shipmentId));

    assertThat(booked).containsExactly(shipmentId);
  }

  @Test
  public void aReservationIsNeverBookedBeforeItIsSent() {
    var booked = new ArrayList<ShipmentId>();
    configure((bookedShipmentId, productItems) -> {
      booked.add(bookedShipmentId);
      return SilentDeliveryProvider.booking;
    });

    commandBus.send(new ReserveStock(referenceId, available));

    assertThat(booked).isEmpty();
  }

  @Test
  public void outOfStockShipmentIsNeverBooked() {
    var booked = new ArrayList<ShipmentId>();
    configure((bookedShipmentId, productItems) -> {
      booked.add(bookedShipmentId);
      return SilentDeliveryProvider.booking;
    });

    commandBus.send(new ReserveStock(referenceId, new ProductItem[]{unavailableItem}));
    commandBus.send(new SendPackage(shipmentId));

    assertThat(booked).isEmpty();
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
