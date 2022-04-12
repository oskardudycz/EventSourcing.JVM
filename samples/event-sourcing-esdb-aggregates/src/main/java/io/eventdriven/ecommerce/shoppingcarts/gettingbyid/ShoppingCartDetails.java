package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.eventdriven.ecommerce.core.events.EventMetadata;
import io.eventdriven.ecommerce.core.views.VersionedView;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartStatus;

import javax.persistence.*;
import java.util.List;
import java.util.UUID;

@Entity
public class ShoppingCartDetails implements VersionedView {
  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID clientId;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private ShoppingCartStatus status;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name="shopping_cart_id")
  private List<ShoppingCartDetailsProductItem> productItems;

  @JsonIgnore
  @Column(nullable = false)
  private long version;

  @JsonIgnore
  @Column(nullable = false)
  private long lastProcessedPosition;

  public ShoppingCartDetails(
    UUID id,
    UUID clientId,
    ShoppingCartStatus status,
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

  public ShoppingCartStatus getStatus() {
    return status;
  }

  public ShoppingCartDetails setStatus(ShoppingCartStatus status) {
    this.status = status;
    return this;
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

  @JsonIgnore
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
