package io.eventdriven.ecommerce.shoppingcarts.gettingcarts;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.eventdriven.ecommerce.core.events.EventMetadata;
import io.eventdriven.ecommerce.core.views.VersionedView;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartStatus;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;

import javax.persistence.*;
import java.util.UUID;

@Entity
public class ShoppingCartShortInfo implements VersionedView {
  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID clientId;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private ShoppingCartStatus status;

  @Column(nullable = false)
  private int totalItemsCount;

  @Column(nullable = false)
  private double totalPrice;

  @JsonIgnore
  @Column(nullable = false)
  private long version;

  @JsonIgnore
  @Column(nullable = false)
  private long lastProcessedPosition;

  public ShoppingCartShortInfo(
    UUID id,
    UUID clientId,
    ShoppingCartStatus status,
    int totalItemsCount,
    double totalPrice,
    long version,
    long lastProcessedPosition
  ) {
    this.id = id;
    this.clientId = clientId;
    this.status = status;
    this.totalItemsCount = totalItemsCount;
    this.totalPrice = totalPrice;
    this.version = version;
    this.lastProcessedPosition = lastProcessedPosition;
  }

  public ShoppingCartShortInfo() {

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

  public ShoppingCartShortInfo setStatus(ShoppingCartStatus status) {
    this.status = status;
    return this;
  }

  public int getTotalItemsCount() {
    return totalItemsCount;
  }

  public void setTotalItemsCount(int totalItemsCount) {
    this.totalItemsCount = totalItemsCount;
  }

  public double getTotalPrice() {
    return totalPrice;
  }

  public void setTotalPrice(double totalPrice) {
    this.totalPrice = totalPrice;
  }

  public long getLastProcessedPosition() {
    return lastProcessedPosition;
  }

  public ShoppingCartShortInfo increaseProducts(PricedProductItem productItem) {
    totalItemsCount += productItem.quantity();
    totalPrice += productItem.totalPrice();

    return this;
  }

  public ShoppingCartShortInfo decreaseProducts(PricedProductItem productItem) {
    totalItemsCount -= productItem.quantity();
    totalPrice -= productItem.totalPrice();

    return this;
  }

  public void setLastProcessedPosition(long lastProcessedPosition) {
    this.lastProcessedPosition = lastProcessedPosition;
  }

  @JsonIgnore
  public void setMetadata(EventMetadata eventMetadata) {
    this.version = eventMetadata.streamPosition();
    this.lastProcessedPosition = eventMetadata.logPosition();
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }
}
