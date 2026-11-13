package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.testing.AggregateSpecification;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;

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
  private final String carrier = "DHL";
  private final String trackingNumber = "JD014600006281234567";

  private final StockReserved reserved =
    new StockReserved(shipmentId, referenceId, available, now);
  private final FulfillmentRequested fulfillmentRequested =
    new FulfillmentRequested(shipmentId, referenceId, available, now);
  private final FulfillmentRejected rejected =
    new FulfillmentRejected(shipmentId, referenceId, now);
  private final PackageWasSent sent =
    new PackageWasSent(shipmentId, referenceId, carrier, trackingNumber, now);

  protected ShipmentTests() {
    super(Shipment::empty);
  }

  @Test
  public void reservingWithEveryProductAvailableEmitsStockReserved() {
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
  public void sendingAReservedShipmentEmitsFulfillmentRequested() {
    // Given
    given(reserved)
      // When
      .when(current -> current.requestFulfillment(now))
      // Then
      .then(fulfillmentRequested);
  }

  @Test
  public void requestingFulfillmentTwiceEmitsNothing() {
    // Given
    given(reserved, fulfillmentRequested)
      // When
      .when(current -> current.requestFulfillment(now))
      // Then
      .thenNothing();
  }

  @Test
  public void sendingWithoutAReservationEmitsNothing() {
    // Given
    given()
      // When
      .when(current -> current.requestFulfillment(now))
      // Then
      .thenNothing();
  }

  @Test
  public void sendingAnAlreadySentPackageEmitsNothing() {
    // Given
    given(reserved, fulfillmentRequested, sent)
      // When
      .when(current -> current.requestFulfillment(now))
      // Then
      .thenNothing();
  }

  @Test
  public void confirmingThePackageSentEmitsPackageWasSentWithTheCarrierAndTrackingNumber() {
    // Given
    given(reserved, fulfillmentRequested)
      // When
      .when(current -> current.confirmSent(carrier, trackingNumber, now))
      // Then
      .then(sent);
  }

  @Test
  public void confirmingAPackageSentWithoutAFulfillmentRequestEmitsNothing() {
    // Given
    given(reserved)
      // When
      .when(current -> current.confirmSent(carrier, trackingNumber, now))
      // Then
      .thenNothing();
  }

  @Test
  public void confirmingThePackageSentTwiceEmitsNothing() {
    // Given
    given(reserved, fulfillmentRequested, sent)
      // When
      .when(current -> current.confirmSent(carrier, trackingNumber, now))
      // Then
      .thenNothing();
  }

  @Test
  public void confirmingThePackageSentAfterTheStockWasReleasedEmitsNothing() {
    // Given
    given(reserved, fulfillmentRequested, new StockReleased(shipmentId, referenceId, now))
      // When
      .when(current -> current.confirmSent(carrier, trackingNumber, now))
      // Then
      .thenNothing();
  }

  @Test
  public void deliveringSentPackageEmitsPackageWasDelivered() {
    // Given
    given(reserved, fulfillmentRequested, sent)
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
    given(reserved, fulfillmentRequested, sent, new PackageWasDelivered(shipmentId, referenceId, now))
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
  public void releasingStockWaitingForFulfillmentEmitsStockReleased() {
    // Given
    given(reserved, fulfillmentRequested)
      // When
      .when(current -> current.releaseStock(now))
      // Then
      .then(new StockReleased(shipmentId, referenceId, now));
  }

  @Test
  public void releasingASentPackageEmitsNothingBecauseTheGoodsHaveLeft() {
    // Given
    given(reserved, fulfillmentRequested, sent)
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
  public void rejectingARequestedFulfillmentEmitsFulfillmentRejected() {
    // Given
    given(reserved, fulfillmentRequested)
      // When
      .when(current -> current.rejectFulfillment(now))
      // Then
      .then(rejected);
  }

  @Test
  public void rejectingWithoutAFulfillmentRequestEmitsNothing() {
    // Given
    given(reserved)
      // When
      .when(current -> current.rejectFulfillment(now))
      // Then
      .thenNothing();
  }

  @Test
  public void rejectingAfterThePackageWasSentEmitsNothing() {
    // Given
    given(reserved, fulfillmentRequested, sent)
      // When
      .when(current -> current.rejectFulfillment(now))
      // Then
      .thenNothing();
  }

  @Test
  public void rejectingTwiceEmitsNothing() {
    // Given
    given(reserved, fulfillmentRequested, rejected)
      // When
      .when(current -> current.rejectFulfillment(now))
      // Then
      .thenNothing();
  }

  @Test
  public void confirmingThePackageSentAfterARejectionEmitsNothing() {
    // Given
    given(reserved, fulfillmentRequested, rejected)
      // When
      .when(current -> current.confirmSent(carrier, trackingNumber, now))
      // Then
      .thenNothing();
  }

  @Test
  public void releasingStockAfterARejectionEmitsStockReleased() {
    // Given
    given(reserved, fulfillmentRequested, rejected)
      // When
      .when(current -> current.releaseStock(now))
      // Then
      .then(new StockReleased(shipmentId, referenceId, now));
  }

  private java.util.function.Consumer<Shipment> reserveStock(ProductItem[] productItems) {
    return current -> current.reserveStock(
      isProductAvailable, shipmentId, referenceId, productItems, now
    );
  }
}
