package io.eventdriven.distributedprocesses.ecommerce.shipments.reservation;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentFacade;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

public class StockReservationsTests {
  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(referenceId);
  private final ProductItem[] productItems = new ProductItem[]{new ProductItem(UUID.randomUUID(), 2)};
  private final OffsetDateTime reservedAt = OffsetDateTime.now();
  private final OffsetDateTime reservedUntil = reservedAt.plusHours(2);
  private final OffsetDateTime beforeExpiry = reservedUntil.minusMinutes(1);
  private final OffsetDateTime afterExpiry = reservedUntil.plusMinutes(1);

  private final Database database = new Database();
  private final StockReservations stockReservations = new StockReservations(database);

  @Test
  public void reservationExpiresOnceItsDeadlinePasses() {
    reserve();

    assertThat(stockReservations.expiredAt(afterExpiry)).containsExactly(shipmentId);
  }

  @Test
  public void reservationDoesNotExpireBeforeItsDeadline() {
    reserve();

    assertThat(stockReservations.expiredAt(beforeExpiry)).isEmpty();
  }

  @Test
  public void reservationExpiresOnItsDeadline() {
    reserve();

    assertThat(stockReservations.expiredAt(reservedUntil)).containsExactly(shipmentId);
  }

  @Test
  public void sentPackageLeavesNoReservationToExpire() {
    reserve();

    stockReservations.onPackageWasSent(envelope(
      new PackageWasSent(shipmentId, referenceId, productItems, "DHL", "JD014600006281234567", reservedAt), 1
    ));

    assertThat(stockReservations.expiredAt(afterExpiry)).isEmpty();
  }

  @Test
  public void releasedStockLeavesNoReservationToExpire() {
    reserve();

    stockReservations.onStockReleased(envelope(
      new StockReleased(shipmentId, referenceId, reservedAt), 1
    ));

    assertThat(stockReservations.expiredAt(afterExpiry)).isEmpty();
  }

  @Test
  public void expiredReservationIsNeverExpiredTwice() {
    reserve();

    stockReservations.onStockReservationExpired(envelope(
      new StockReservationExpired(shipmentId, referenceId, afterExpiry), 1
    ));

    assertThat(stockReservations.expiredAt(afterExpiry)).isEmpty();
  }

  @Test
  public void redeliveringTheSameReservationLeavesASingleEntry() {
    reserve();
    reserve();

    assertThat(stockReservations.expiredAt(afterExpiry)).containsExactly(shipmentId);
  }

  private void reserve() {
    stockReservations.onStockReserved(envelope(
      new StockReserved(shipmentId, referenceId, productItems, reservedAt, reservedUntil), 0
    ));
  }

  private <Event> EventEnvelope<Event> envelope(Event event, long streamPosition) {
    return new EventEnvelope<>(
      event,
      new EventMetadata(ShipmentFacade.mapToStreamId(shipmentId), streamPosition, streamPosition)
    );
  }
}
