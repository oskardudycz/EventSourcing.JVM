package io.eventdriven.introductiontoeventsourcing.e08_projections_singlestream;

import io.eventdriven.introductiontoeventsourcing.e08_projections_singlestream.ProjectionsTests.PricedProductItem;
import io.eventdriven.introductiontoeventsourcing.e08_projections_singlestream.ProjectionsTests.ShoppingCartStatus;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Projections {
  public static class ShoppingCartDetails {
    private UUID id;
    private UUID clientId;
    private ShoppingCartStatus status;
    private List<PricedProductItem> productItems =new ArrayList<>();
    private OffsetDateTime confirmedAt;
    private OffsetDateTime canceledAt;
    private double totalPrice;
    private double totalItemsCount;

    public UUID id() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID clientId() {
      return clientId;
    }

    public void setClientId(UUID clientId) {
      this.clientId = clientId;
    }

    public ShoppingCartStatus status() {
      return status;
    }

    public void setStatus(ShoppingCartStatus status) {
      this.status = status;
    }

    public List<PricedProductItem> productItems() {
      return productItems;
    }

    public void setProductItems(List<PricedProductItem> productItems) {
      this.productItems = productItems;
    }

    public OffsetDateTime confirmedAt() {
      return confirmedAt;
    }

    public void setConfirmedAt(OffsetDateTime confirmedAt) {
      this.confirmedAt = confirmedAt;
    }

    public OffsetDateTime canceledAt() {
      return canceledAt;
    }

    public void setCanceledAt(OffsetDateTime canceledAt) {
      this.canceledAt = canceledAt;
    }

    public double totalPrice() {
      return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
      this.totalPrice = totalPrice;
    }

    public double totalItemsCount() {
      return totalItemsCount;
    }

    public void setTotalItemsCount(double totalItemsCount) {
      this.totalItemsCount = totalItemsCount;
    }
  }

  public static class ShoppingCartShortInfo {
    private UUID id;
    private UUID clientId;
    private double totalPrice;
    private double totalItemsCount;

    public UUID id() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID clientId() {
      return clientId;
    }

    public void setClientId(UUID clientId) {
      this.clientId = clientId;
    }

    public double totalPrice() {
      return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
      this.totalPrice = totalPrice;
    }

    public double totalItemsCount() {
      return totalItemsCount;
    }

    public void setTotalItemsCount(double totalItemsCount) {
      this.totalItemsCount = totalItemsCount;
    }
  }
}
