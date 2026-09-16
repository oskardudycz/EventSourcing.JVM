package io.eventdriven.distributedprocesses.ecommerce.shipments;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;

public class StockReservations {
  private final Map<ShipmentId, OffsetDateTime> reservedUntil = new HashMap<>();

  public void on(StockReserved event) {
    reservedUntil.put(event.shipmentId(), event.reservedUntil());
  }

  public void on(PackageWasSent event) {
    reservedUntil.remove(event.shipmentId());
  }

  public void on(StockReleased event) {
    reservedUntil.remove(event.shipmentId());
  }

  public void on(StockReservationExpired event) {
    reservedUntil.remove(event.shipmentId());
  }

  public List<ShipmentId> expiredAt(OffsetDateTime now) {
    return reservedUntil.entrySet().stream()
      .filter(entry -> !entry.getValue().isAfter(now))
      .map(Map.Entry::getKey)
      .toList();
  }
}
