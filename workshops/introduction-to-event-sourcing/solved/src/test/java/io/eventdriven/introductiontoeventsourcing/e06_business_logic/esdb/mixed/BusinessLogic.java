package io.eventdriven.introductiontoeventsourcing.e06_business_logic.esdb.mixed;

import java.time.OffsetDateTime;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e06_business_logic.esdb.mixed.BusinessLogicTests.*;
import static io.eventdriven.introductiontoeventsourcing.e06_business_logic.esdb.mixed.BusinessLogicTests.ShoppingCartEvent.*;

public class BusinessLogic {
  public interface Aggregate<ShoppingCartEvent> {
    UUID id();

    void when(ShoppingCartEvent event);
  }

  public static class ShoppingCart implements Aggregate<ShoppingCartEvent> {
    private UUID id;
    private UUID clientId;
    private ShoppingCartStatus status;
    private List<PricedProductItem> productItems;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime canceledAt;

    private ShoppingCart() {
    }

    private ShoppingCart(
      ShoppingCartOpened event
    ) {
      when(event);
    }

    public static ShoppingCart empty(){
      return new ShoppingCart();
    }

    public static SimpleEntry<ShoppingCartEvent, ShoppingCart> open(UUID shoppingCartId, UUID clientId) {
      var event = new ShoppingCartOpened(
        shoppingCartId,
        clientId
      );

      return new SimpleEntry<>(event, new ShoppingCart(event));
    }

    ProductItemAddedToShoppingCart addProduct(
      ProductPriceCalculator productPriceCalculator,
      ProductItem productItem
    ) {
      if (isClosed())
        throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(status));

      var pricedProductItem = productPriceCalculator.calculate(productItem);

      return apply(new ProductItemAddedToShoppingCart(
        id,
        pricedProductItem
      ));
    }

    ProductItemRemovedFromShoppingCart removeProduct(
      PricedProductItem productItem
    ) {
      if (isClosed())
        throw new IllegalStateException("Adding product item for cart in '%s' status is not allowed.".formatted(status));

      if (!hasEnough(productItem))
        throw new IllegalStateException("Not enough product items to remove");

      return apply(new ProductItemRemovedFromShoppingCart(
        id,
        productItem
      ));
    }

    ShoppingCartConfirmed confirm() {
      if (isClosed())
        throw new IllegalStateException("Confirming cart in '%s' status is not allowed.".formatted(status));

      return apply(new ShoppingCartConfirmed(
        id,
        OffsetDateTime.now()
      ));
    }

    ShoppingCartCanceled cancel() {
      if (isClosed())
        throw new IllegalStateException("Canceling cart in '%s' status is not allowed.".formatted(status));

      return apply(new ShoppingCartCanceled(
        id,
        OffsetDateTime.now()
      ));
    }

    private boolean isClosed() {
      return status == ShoppingCartStatus.Confirmed || status == ShoppingCartStatus.Canceled;
    }

    public boolean hasEnough(PricedProductItem productItem) {
      var currentQuantity = productItems.stream()
        .filter(pi -> pi.productId().equals(productItem.productId()))
        .mapToInt(PricedProductItem::quantity)
        .sum();

      return currentQuantity >= productItem.quantity();
    }

    public UUID id() {
      return id;
    }

    public UUID clientId() {
      return clientId;
    }

    public ShoppingCartStatus status() {
      return status;
    }

    public PricedProductItem[] productItems() {
      return productItems.toArray(PricedProductItem[]::new);
    }

    public OffsetDateTime confirmedAt() {
      return confirmedAt;
    }

    public OffsetDateTime canceledAt() {
      return canceledAt;
    }

    @Override
    public void when(ShoppingCartEvent event) {
      switch (event) {
        case ShoppingCartOpened opened -> apply(opened);
        case ProductItemAddedToShoppingCart productItemAdded ->
          apply(productItemAdded);
        case ProductItemRemovedFromShoppingCart productItemRemoved ->
          apply(productItemRemoved);
        case ShoppingCartConfirmed confirmed -> apply(confirmed);
        case ShoppingCartCanceled canceled -> apply(canceled);
      }
    }

    private void apply(ShoppingCartOpened event) {
      id = event.shoppingCartId();
      clientId = event.clientId();
      status = ShoppingCartStatus.Pending;
      productItems = new ArrayList<>();
    }

    private ProductItemAddedToShoppingCart apply(ProductItemAddedToShoppingCart event) {
      var pricedProductItem = event.productItem();
      var productId = pricedProductItem.productId();
      var quantityToAdd = pricedProductItem.quantity();

      productItems.stream()
        .filter(pi -> pi.productId().equals(productId))
        .findAny()
        .ifPresentOrElse(
          current -> productItems.set(
            productItems.indexOf(current),
            new PricedProductItem(current.productId(), current.quantity() + quantityToAdd, current.unitPrice())
          ),
          () -> productItems.add(pricedProductItem)
        );
      return event;
    }

    private ProductItemRemovedFromShoppingCart apply(ProductItemRemovedFromShoppingCart event) {
      var pricedProductItem = event.productItem();
      var productId = pricedProductItem.productId();
      var quantityToRemove = pricedProductItem.quantity();

      productItems.stream()
        .filter(pi -> pi.productId().equals(productId))
        .findAny()
        .ifPresent(
          current -> productItems.set(
            productItems.indexOf(current),
            new PricedProductItem(current.productId(), current.quantity() - quantityToRemove, current.unitPrice())
          )
        );

      return event;
    }

    private ShoppingCartConfirmed apply(ShoppingCartConfirmed event) {
      status = ShoppingCartStatus.Confirmed;
      confirmedAt = event.confirmedAt();
      return event;
    }

    private ShoppingCartCanceled apply(ShoppingCartCanceled event) {
      status = ShoppingCartStatus.Canceled;
      canceledAt = event.canceledAt();
      return event;
    }
  }

  public enum ShoppingCartStatus {
    Pending,
    Confirmed,
    Canceled
  }

  public sealed interface ShoppingCartCommand {
    record OpenShoppingCart(
      UUID shoppingCartId,
      UUID clientId
    ) implements ShoppingCartCommand {
    }

    record AddProductItemToShoppingCart(
      UUID shoppingCartId,
      ProductItem productItem
    ) implements ShoppingCartCommand {
    }

    record RemoveProductItemFromShoppingCart(
      UUID shoppingCartId,
      PricedProductItem productItem
    ) implements ShoppingCartCommand {
    }

    record ConfirmShoppingCart(
      UUID shoppingCartId
    ) implements ShoppingCartCommand {
    }

    record CancelShoppingCart(
      UUID shoppingCartId
    ) implements ShoppingCartCommand {
    }
  }

  public interface ProductPriceCalculator {
    PricedProductItem calculate(ProductItem productItems);
  }

  public static class FakeProductPriceCalculator implements ProductPriceCalculator {
    private final double value;

    private FakeProductPriceCalculator(double value) {
      this.value = value;
    }

    public static FakeProductPriceCalculator returning(double value) {
      return new FakeProductPriceCalculator(value);
    }

    public PricedProductItem calculate(ProductItem productItem) {
      return new PricedProductItem(productItem.productId(), productItem.quantity(), value);
    }
  }
}
