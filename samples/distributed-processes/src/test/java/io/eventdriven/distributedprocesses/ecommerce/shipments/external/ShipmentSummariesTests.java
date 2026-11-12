package io.eventdriven.distributedprocesses.ecommerce.shipments.external;

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

public class ShipmentSummariesTests {
  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(referenceId);
  private final ProductItem[] productItems = new ProductItem[]{new ProductItem(UUID.randomUUID(), 2)};
  private final OffsetDateTime reservedAt = OffsetDateTime.now();
  private final OffsetDateTime reservedUntil = reservedAt.plusHours(2);
  private final OffsetDateTime sentAt = reservedAt.plusMinutes(5);
  private final OffsetDateTime deliveredAt = reservedAt.plusMinutes(30);

  private final Database database = new Database();
  private final ShipmentSummaries summaries = new ShipmentSummaries(database);

  @Test
  public void reservingStoresTheSummaryWithItsDeadline() {
    reserve();

    var summary = summaries.get(shipmentId).orElseThrow();

    assertThat(summary.getShipmentId()).isEqualTo(shipmentId);
    assertThat(summary.getReferenceId()).isEqualTo(referenceId);
    assertThat(summary.getProductItems()).containsExactly(productItems);
    assertThat(summary.getStatus()).isEqualTo(ShipmentSummary.Status.Reserved);
    assertThat(summary.getReservedAt()).isEqualTo(reservedAt);
    assertThat(summary.getReservedUntil()).isEqualTo(reservedUntil);
  }

  @Test
  public void runningOutOfStockStoresTheSummaryWithoutAReservation() {
    summaries.onProductWasOutOfStock(envelope(
      new ProductWasOutOfStock(shipmentId, referenceId, productItems, reservedAt), 0
    ));

    var summary = summaries.get(shipmentId).orElseThrow();

    assertThat(summary.getProductItems()).containsExactly(productItems);
    assertThat(summary.getStatus()).isEqualTo(ShipmentSummary.Status.OutOfStock);
    assertThat(summary.getReservedAt()).isNull();
    assertThat(summary.getReservedUntil()).isNull();
  }

  @Test
  public void sendingMarksTheSummaryAsSent() {
    reserve();

    summaries.onPackageWasSent(sent());

    assertThat(statusOf(shipmentId)).isEqualTo(ShipmentSummary.Status.Sent);
  }

  @Test
  public void deliveringMarksTheSummaryAsDelivered() {
    reserve();
    summaries.onPackageWasSent(sent());

    summaries.onPackageWasDelivered(envelope(
      new PackageWasDelivered(shipmentId, referenceId, deliveredAt), 2
    ));

    assertThat(statusOf(shipmentId)).isEqualTo(ShipmentSummary.Status.Delivered);
  }

  @Test
  public void releasingMarksTheSummaryAsReleased() {
    reserve();

    summaries.onStockReleased(envelope(
      new StockReleased(shipmentId, referenceId, sentAt), 1
    ));

    assertThat(statusOf(shipmentId)).isEqualTo(ShipmentSummary.Status.Released);
  }

  @Test
  public void expiringMarksTheSummaryAsExpiredAndKeepsTheReservedItems() {
    reserve();

    summaries.onStockReservationExpired(envelope(
      new StockReservationExpired(shipmentId, referenceId, reservedUntil), 1
    ));

    var summary = summaries.get(shipmentId).orElseThrow();

    assertThat(summary.getStatus()).isEqualTo(ShipmentSummary.Status.Expired);
    assertThat(summary.getProductItems()).containsExactly(productItems);
  }

  @Test
  public void redeliveringTheSameEventLeavesTheSummaryWhereItWas() {
    reserve();
    var sent = sent();
    summaries.onPackageWasSent(sent);

    summaries.onPackageWasSent(sent);

    var summary = summaries.get(shipmentId).orElseThrow();

    assertThat(summary.getStatus()).isEqualTo(ShipmentSummary.Status.Sent);
    assertThat(summary.getVersion()).isEqualTo(1);
  }

  @Test
  public void redeliveringAnEarlierEventNeverUndoesALaterOne() {
    reserve();
    var sent = sent();
    summaries.onPackageWasSent(sent);
    summaries.onPackageWasDelivered(envelope(
      new PackageWasDelivered(shipmentId, referenceId, deliveredAt), 2
    ));

    summaries.onPackageWasSent(sent);

    assertThat(statusOf(shipmentId)).isEqualTo(ShipmentSummary.Status.Delivered);
  }

  @Test
  public void shipmentThatWasNeverReservedHasNoSummary() {
    assertThat(summaries.get(shipmentId)).isEmpty();
  }

  private void reserve() {
    summaries.onStockReserved(envelope(
      new StockReserved(shipmentId, referenceId, productItems, reservedAt, reservedUntil), 0
    ));
  }

  private EventEnvelope<PackageWasSent> sent() {
    return envelope(new PackageWasSent(shipmentId, referenceId, productItems, "DHL", "JD014600006281234567", sentAt), 1);
  }

  private ShipmentSummary.Status statusOf(ShipmentId shipmentId) {
    return summaries.get(shipmentId).orElseThrow().getStatus();
  }

  private <Event> EventEnvelope<Event> envelope(Event event, long streamPosition) {
    return new EventEnvelope<>(
      event,
      new EventMetadata(ShipmentFacade.mapToStreamId(shipmentId), streamPosition, streamPosition)
    );
  }
}
