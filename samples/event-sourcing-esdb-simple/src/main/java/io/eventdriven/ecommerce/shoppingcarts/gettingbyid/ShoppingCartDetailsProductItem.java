package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.UUID;

@Entity
public class ShoppingCartDetailsProductItem {
  @Id
  private UUID productId;

  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false)
  private double unitPrice;

  public ShoppingCartDetailsProductItem(UUID productId, int quantity, double unitPrice) {
    this.productId = productId;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
  }

  public ShoppingCartDetailsProductItem() {

  }

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

  public void increaseQuantity(int quantity) {
    this.quantity += quantity;
  }

  public void decreaseQuantity(int quantity) {
    this.quantity -= quantity;
  }

  public double getUnitPrice() {
    return unitPrice;
  }

  public void setUnitPrice(double unitPrice) {
    this.unitPrice = unitPrice;
  }
}
