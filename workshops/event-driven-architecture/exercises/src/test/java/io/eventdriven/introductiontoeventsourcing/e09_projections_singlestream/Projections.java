package io.eventdriven.introductiontoeventsourcing.e09_projections_singlestream;

import io.eventdriven.introductiontoeventsourcing.e09_projections_singlestream.ProjectionsTests.PricedProductItem;
import io.eventdriven.introductiontoeventsourcing.e09_projections_singlestream.ProjectionsTests.ShoppingCartStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class Projections {
  public static class ShoppingCartDetails {
    private UUID id;
    private UUID clientId;
    private ShoppingCartStatus status;
    private List<PricedProductItem> productItems;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime canceledAt;
    private double totalAmount;
    private double totalItemsCount;

    public ShoppingCartDetails(){}

    public ShoppingCartDetails(UUID id, UUID clientId, ShoppingCartStatus status, List<PricedProductItem> productItems, OffsetDateTime confirmedAt, OffsetDateTime canceledAt, double totalAmount, double totalItemsCount) {
      this.id = id;
      this.clientId = clientId;
      this.status = status;
      this.productItems = productItems;
      this.confirmedAt = confirmedAt;
      this.canceledAt = canceledAt;
      this.totalAmount = totalAmount;
      this.totalItemsCount = totalItemsCount;
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID getClientId() {
      return clientId;
    }

    public void setClientId(UUID clientId) {
      this.clientId = clientId;
    }

    public ShoppingCartStatus getStatus() {
      return status;
    }

    public void setStatus(ShoppingCartStatus status) {
      this.status = status;
    }

    public List<PricedProductItem> getProductItems() {
      return productItems;
    }

    public void setProductItems(List<PricedProductItem> productItems) {
      this.productItems = productItems;
    }

    public OffsetDateTime getConfirmedAt() {
      return confirmedAt;
    }

    public void setConfirmedAt(OffsetDateTime confirmedAt) {
      this.confirmedAt = confirmedAt;
    }

    public OffsetDateTime getCanceledAt() {
      return canceledAt;
    }

    public void setCanceledAt(OffsetDateTime canceledAt) {
      this.canceledAt = canceledAt;
    }

    public double getTotalAmount() {
      return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
      this.totalAmount = totalAmount;
    }

    public double getTotalItemsCount() {
      return totalItemsCount;
    }

    public void setTotalItemsCount(double totalItemsCount) {
      this.totalItemsCount = totalItemsCount;
    }
  }

  public static class ShoppingCartShortInfo {
    private UUID id;
    private UUID clientId;
    private double totalAmount;
    private double totalItemsCount;

    public ShoppingCartShortInfo() {
    }

    public ShoppingCartShortInfo(UUID id, UUID clientId, double totalAmount, double totalItemsCount) {
      this.id = id;
      this.clientId = clientId;
      this.totalAmount = totalAmount;
      this.totalItemsCount = totalItemsCount;
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID getClientId() {
      return clientId;
    }

    public void setClientId(UUID clientId) {
      this.clientId = clientId;
    }

    public double getTotalAmount() {
      return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
      this.totalAmount = totalAmount;
    }

    public double getTotalItemsCount() {
      return totalItemsCount;
    }

    public void setTotalItemsCount(double totalItemsCount) {
      this.totalItemsCount = totalItemsCount;
    }
  }
}
