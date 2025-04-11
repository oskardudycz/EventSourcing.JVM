package io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.mixed.app.shoppingcarts.productItems;

import java.util.UUID;

public final class ProductItems {
  public static class ProductItem {
    private UUID productId;
    private int quantity;

    public ProductItem(UUID productId, int quantity) {
      this.setProductId(productId);
      this.setQuantity(quantity);
    }

    public ProductItem(){}

    public UUID getProductId() {
      return productId;
    }

    public void setProductId(UUID productId) {
      this.productId = productId;
    }

    public int getQuantity() {
      return quantity;
    }

    public void setQuantity(int quantity) {
      this.quantity = quantity;
    }

    public void add(int quantity) {
      this.quantity += quantity;
    }

    public void subtract(int quantity) {
      this.quantity -= quantity;
    }
  }

  public static class PricedProductItem {
    private UUID productId;
    private double unitPrice;
    private int quantity;

    public PricedProductItem(UUID productId, int quantity, double unitPrice) {
      this.setProductId(productId);
      this.setUnitPrice(unitPrice);
      this.setQuantity(quantity);
    }

    public PricedProductItem(){}

    private double getTotalAmount() {
      return getQuantity() * getUnitPrice();
    }

    public UUID getProductId() {
      return productId;
    }

    public void setProductId(UUID productId) {
      this.productId = productId;
    }

    public double getUnitPrice() {
      return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
      this.unitPrice = unitPrice;
    }

    public int getQuantity() {
      return quantity;
    }

    public void setQuantity(int quantity) {
      this.quantity = quantity;
    }

    public void add(int quantity) {
      this.quantity += quantity;
    }

    public void subtract(int quantity) {
      this.quantity -= quantity;
    }
  }
}
