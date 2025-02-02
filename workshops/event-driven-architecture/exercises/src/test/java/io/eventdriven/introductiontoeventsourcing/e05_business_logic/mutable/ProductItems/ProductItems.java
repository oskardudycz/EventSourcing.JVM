package io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable.ProductItems;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonCreator;

public final class ProductItems {
  public static class ProductItem {
    private UUID productId;
    private int quantity;

    @JsonCreator
    public ProductItem(UUID productId, int quantity) {
      this.setProductId(productId);
      this.setQuantity(quantity);
    }

    public UUID productId() {
      return productId;
    }

    public void setProductId(UUID productId) {
      this.productId = productId;
    }

    public int quantity() {
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

    @JsonCreator
    public PricedProductItem(UUID productId, int quantity, double unitPrice) {
      this.setProductId(productId);
      this.setUnitPrice(unitPrice);
      this.setQuantity(quantity);
    }

    private double totalAmount() {
      return quantity() * unitPrice();
    }

    public UUID productId() {
      return productId;
    }

    public void setProductId(UUID productId) {
      this.productId = productId;
    }

    public double unitPrice() {
      return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
      this.unitPrice = unitPrice;
    }

    public int quantity() {
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
