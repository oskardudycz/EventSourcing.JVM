package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.projections.Versioned;

import java.time.OffsetDateTime;

public class StockReservation implements Versioned {
  private ShipmentId shipmentId;
  private OffsetDateTime reservedUntil;
  private long version;

  public StockReservation() {
  }

  public StockReservation(ShipmentId shipmentId, OffsetDateTime reservedUntil) {
    this.shipmentId = shipmentId;
    this.reservedUntil = reservedUntil;
  }

  public ShipmentId getShipmentId() {
    return shipmentId;
  }

  public void setShipmentId(ShipmentId shipmentId) {
    this.shipmentId = shipmentId;
  }

  public OffsetDateTime getReservedUntil() {
    return reservedUntil;
  }

  public void setReservedUntil(OffsetDateTime reservedUntil) {
    this.reservedUntil = reservedUntil;
  }

  @Override
  public long getVersion() {
    return version;
  }

  @Override
  public void setVersion(long version) {
    this.version = version;
  }
}
