package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.projections.Versioned;

import java.time.OffsetDateTime;

public class ShipmentSummary implements Versioned {
  public enum Status {
    Reserved,
    Sent,
    Delivered,
    Released,
    Expired,
    OutOfStock
  }

  private ShipmentId shipmentId;
  private String referenceId;
  private ProductItem[] productItems;
  private Status status;
  private OffsetDateTime reservedAt;
  private OffsetDateTime reservedUntil;
  private long version;

  public ShipmentSummary() {
  }

  public ShipmentSummary(
    ShipmentId shipmentId,
    String referenceId,
    ProductItem[] productItems,
    Status status,
    OffsetDateTime reservedAt,
    OffsetDateTime reservedUntil
  ) {
    this.shipmentId = shipmentId;
    this.referenceId = referenceId;
    this.productItems = productItems;
    this.status = status;
    this.reservedAt = reservedAt;
    this.reservedUntil = reservedUntil;
  }

  public ShipmentId getShipmentId() {
    return shipmentId;
  }

  public void setShipmentId(ShipmentId shipmentId) {
    this.shipmentId = shipmentId;
  }

  public String getReferenceId() {
    return referenceId;
  }

  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }

  public ProductItem[] getProductItems() {
    return productItems;
  }

  public void setProductItems(ProductItem[] productItems) {
    this.productItems = productItems;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public OffsetDateTime getReservedAt() {
    return reservedAt;
  }

  public void setReservedAt(OffsetDateTime reservedAt) {
    this.reservedAt = reservedAt;
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
