package io.eventdriven.ecommerce.shoppingcarts.gettingcartbyid;

import io.eventdriven.ecommerce.core.events.EventMetadata;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;

import javax.persistence.*;
import java.util.List;
import java.util.UUID;

@Entity
public class ShoppingCartDetails {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID clientId;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private ShoppingCart.Status status;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name="shopping_cart_id")
  private List<ShoppingCartDetailsProductItem> productItems;

  @Column(nullable = false)
  private long version;

  @Column(nullable = false)
  private long lastProcessedPosition;

  public ShoppingCartDetails(
    UUID id,
    UUID clientId,
    ShoppingCart.Status status,
    List<ShoppingCartDetailsProductItem> productItems,
    long version,
    long lastProcessedPosition
  ) {
    this.id = id;
    this.clientId = clientId;
    this.status = status;
    this.productItems = productItems;
    this.version = version;
    this.lastProcessedPosition = lastProcessedPosition;
  }

  public ShoppingCartDetails() {

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

  public ShoppingCart.Status getStatus() {
    return status;
  }

  public void setStatus(ShoppingCart.Status status) {
    this.status = status;
  }

  public List<ShoppingCartDetailsProductItem> getProductItems() {
    return productItems;
  }

  public long getLastProcessedPosition() {
    return lastProcessedPosition;
  }

  public void setLastProcessedPosition(long lastProcessedPosition) {
    this.lastProcessedPosition = lastProcessedPosition;
  }

  public void setMetadata(EventMetadata eventMetadata) {
    this.version =  eventMetadata.streamPosition();
    this.lastProcessedPosition = eventMetadata.logPosition();
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }
}
