package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.UUID;

@Entity
class ShoppingCartDetailsProductItem {
  @Id
  private UUID productId;

  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false)
  private double unitPrice;

  ShoppingCartDetailsProductItem(UUID productId, int quantity, double unitPrice) {
    this.productId = productId;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
  }

  ShoppingCartDetailsProductItem() {

  }

  UUID getProductId() {
    return productId;
  }

  void setProductId(UUID productId) {
    this.productId = productId;
  }

  int getQuantity() {
    return quantity;
  }

  void setQuantity(int quantity) {
    this.quantity = quantity;
  }

  void increaseQuantity(int quantity) {
    this.quantity += quantity;
  }

  void decreaseQuantity(int quantity) {
    this.quantity -= quantity;
  }

  double getUnitPrice() {
    return unitPrice;
  }

  void setUnitPrice(double unitPrice) {
    this.unitPrice = unitPrice;
  }
}
