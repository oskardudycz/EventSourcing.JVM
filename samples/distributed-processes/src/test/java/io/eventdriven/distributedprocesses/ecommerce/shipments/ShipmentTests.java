package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.ecommerce.shipments.delivery.ParcelBooking;
import io.eventdriven.testing.AggregateSpecification;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

public class ShipmentTests extends AggregateSpecification<Shipment, ShipmentEvent, ShipmentId> {
  private final ShipmentId shipmentId = ShipmentId.of(UUID.randomUUID());
  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final ProductItem availableItem = new ProductItem(UUID.randomUUID(), 2);
  private final ProductItem unavailableItem = new ProductItem(UUID.randomUUID(), 1);
  private final ProductItem[] available = new ProductItem[]{availableItem};
  private final ProductItem[] partlyAvailable = new ProductItem[]{availableItem, unavailableItem};
  private final Function<ProductItem, Boolean> isProductAvailable =
    item -> Set.of(availableItem).contains(item);
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime reservedUntil = now.plusHours(2);
  private final ParcelBooking booking = new ParcelBooking("DHL", "JD014600006281234567");
  private final BiFunction<ShipmentId, ProductItem[], Optional<ParcelBooking>> bookParcel =
    (bookedShipmentId, productItems) -> Optional.of(booking);

  private final StockReserved reserved =
    new StockReserved(shipmentId, referenceId, available, now, reservedUntil);
  private final PackageWasSent sent = new PackageWasSent(
    shipmentId, referenceId, available, booking.carrier(), booking.trackingNumber(), now
  );

  protected ShipmentTests() {
    super(Shipment::empty);
  }

  @Test
  public void reservingWithEveryProductAvailableEmitsStockReservedWithItsDeadline() {
    // Given
    given()
      // When
      .when(reserveStock(available))
      // Then
      .then(reserved);
  }

  @Test
  public void reservingWithAnUnavailableProductEmitsProductWasOutOfStock() {
    // Given
    given()
      // When
      .when(reserveStock(partlyAvailable))
      // Then
      .then(new ProductWasOutOfStock(shipmentId, referenceId, partlyAvailable, now));
  }

  @Test
  public void reservingAnAlreadyReservedShipmentEmitsNothing() {
    // Given
    given(reserved)
      // When
      .when(reserveStock(available))
      // Then
      .thenNothing();
  }

  @Test
  public void sendingAReservedShipmentEmitsPackageWasSentWithTheBookedTracking() {
    // Given
    given(reserved)
      // When
      .when(current -> current.send(bookParcel, now))
      // Then
      .then(sent);
  }

  @Test
  public void sendingAParcelTheProviderDidNotBookEmitsNothing() {
    // Given
    given(reserved)
      // When
      .when(current -> current.send((bookedShipmentId, productItems) -> Optional.empty(), now))
      // Then
      .thenNothing();
  }

  @Test
  public void sendingAnAlreadySentPackageDoesNotBookASecondParcel() {
    var booked = new ArrayList<ShipmentId>();

    // Given
    given(reserved, sent)
      // When
      .when(current -> current.send((bookedShipmentId, productItems) -> {
        booked.add(bookedShipmentId);
        return Optional.of(booking);
      }, now))
      // Then
      .thenNothing();

    assertThat(booked).isEmpty();
  }

  @Test
  public void sendingWithoutAReservationEmitsNothing() {
    // Given
    given()
      // When
      .when(current -> current.send(bookParcel, now))
      // Then
      .thenNothing();
  }

  @Test
  public void sendingAnAlreadySentPackageEmitsNothing() {
    // Given
    given(reserved, sent)
      // When
      .when(current -> current.send(bookParcel, now))
      // Then
      .thenNothing();
  }

  @Test
  public void deliveringSentPackageEmitsPackageWasDelivered() {
    // Given
    given(reserved, sent)
      // When
      .when(current -> current.deliver(now))
      // Then
      .then(new PackageWasDelivered(shipmentId, referenceId, now));
  }

  @Test
  public void deliveringAReservedButUnsentShipmentEmitsNothing() {
    // Given
    given(reserved)
      // When
      .when(current -> current.deliver(now))
      // Then
      .thenNothing();
  }

  @Test
  public void deliveringAlreadyDeliveredPackageEmitsNothing() {
    // Given
    given(reserved, sent, new PackageWasDelivered(shipmentId, referenceId, now))
      // When
      .when(current -> current.deliver(now))
      // Then
      .thenNothing();
  }

  @Test
  public void releasingAReservedShipmentEmitsStockReleased() {
    // Given
    given(reserved)
      // When
      .when(current -> current.releaseStock(now))
      // Then
      .then(new StockReleased(shipmentId, referenceId, now));
  }

  @Test
  public void releasingASentPackageEmitsNothingBecauseTheGoodsHaveLeft() {
    // Given
    given(reserved, sent)
      // When
      .when(current -> current.releaseStock(now))
      // Then
      .thenNothing();
  }

  @Test
  public void releasingTwiceEmitsNothing() {
    // Given
    given(reserved, new StockReleased(shipmentId, referenceId, now))
      // When
      .when(current -> current.releaseStock(now))
      // Then
      .thenNothing();
  }

  @Test
  public void expiringAReservationEmitsStockReservationExpired() {
    // Given
    given(reserved)
      // When
      .when(current -> current.expireReservation(reservedUntil))
      // Then
      .then(new StockReservationExpired(shipmentId, referenceId, reservedUntil));
  }

  @Test
  public void expiringAReservationOfAPackageThatAlreadyLeftEmitsNothing() {
    // Given
    given(reserved, sent)
      // When
      .when(current -> current.expireReservation(reservedUntil))
      // Then
      .thenNothing();
  }

  @Test
  public void expiringAReleasedReservationEmitsNothing() {
    // Given
    given(reserved, new StockReleased(shipmentId, referenceId, now))
      // When
      .when(current -> current.expireReservation(reservedUntil))
      // Then
      .thenNothing();
  }

  @Test
  public void sendingAnExpiredReservationEmitsNothing() {
    // Given
    given(reserved, new StockReservationExpired(shipmentId, referenceId, reservedUntil))
      // When
      .when(current -> current.send(bookParcel, now))
      // Then
      .thenNothing();
  }

  private java.util.function.Consumer<Shipment> reserveStock(ProductItem[] productItems) {
    return current -> current.reserveStock(
      isProductAvailable, shipmentId, referenceId, productItems, now, reservedUntil
    );
  }
}
