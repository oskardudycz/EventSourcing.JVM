package io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.occurrent.immutable;

import one.util.streamex.StreamEx;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.occurrent.immutable.BusinessLogic.ShoppingCartEvent.*;

public class BusinessLogic {

  public sealed interface ShoppingCartEvent {
    record ShoppingCartOpened(
      UUID shoppingCartId,
      UUID clientId
    ) implements ShoppingCartEvent {
    }

    record ProductItemAddedToShoppingCart(
      UUID shoppingCartId,
      PricedProductItem productItem
    ) implements ShoppingCartEvent {
    }

    record ProductItemRemovedFromShoppingCart(
      UUID shoppingCartId,
      PricedProductItem productItem
    ) implements ShoppingCartEvent {
    }

    record ShoppingCartConfirmed(
      UUID shoppingCartId,
      OffsetDateTime confirmedAt
    ) implements ShoppingCartEvent {
    }

    record ShoppingCartCanceled(
      UUID shoppingCartId,
      OffsetDateTime canceledAt
    ) implements ShoppingCartEvent {
    }
  }

  public static ShoppingCartOpened open(ShoppingCart shoppingCart, UUID shoppingCartId, UUID clientId) {
    if (shoppingCart.isClosed()) {
      throw new IllegalStateException("Cannot open a closed shopping cart");
    }
    return new ShoppingCartOpened(
      shoppingCartId,
      clientId
    );
  }

  public static ProductItemAddedToShoppingCart addProductItem(
    ShoppingCart shoppingCart,
    ProductItem productItem,
    ProductPriceCalculator productPriceCalculator
  ) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    var pricedProductItem = productPriceCalculator.calculate(productItem);

    shoppingCart.productItems().add(pricedProductItem);

    return new ProductItemAddedToShoppingCart(
      shoppingCart.id(),
      pricedProductItem
    );
  }

  public static ProductItemRemovedFromShoppingCart removeProductItem(ShoppingCart shoppingCart, PricedProductItem productItem) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Adding product item for cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    shoppingCart.productItems().hasEnough(productItem);

    return new ProductItemRemovedFromShoppingCart(
      shoppingCart.id(),
      productItem
    );
  }

  public static ShoppingCartConfirmed confirm(ShoppingCart shoppingCart) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Confirming cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    return new ShoppingCartConfirmed(
      shoppingCart.id(),
      OffsetDateTime.now()
    );
  }

  public static ShoppingCartCanceled cancel(ShoppingCart shoppingCart) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Canceling cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    return new ShoppingCartCanceled(
      shoppingCart.id(),
      OffsetDateTime.now()
    );
  }

  interface ProductPriceCalculator {
    PricedProductItem calculate(ProductItem productItems);
  }

  static class FakeProductPriceCalculator implements ProductPriceCalculator {
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


  sealed public interface ShoppingCart {
    UUID id();

    UUID clientId();

    ProductItems productItems();

    record PendingShoppingCart(
      UUID id,
      UUID clientId,
      ProductItems productItems
    ) implements ShoppingCart {
    }

    record ConfirmedShoppingCart(
      UUID id,
      UUID clientId,
      ProductItems productItems,
      OffsetDateTime confirmedAt
    ) implements ShoppingCart {
    }

    record CanceledShoppingCart(
      UUID id,
      UUID clientId,
      ProductItems productItems,
      OffsetDateTime canceledAt
    ) implements ShoppingCart {
    }

    enum ShoppingCartStatus {
      Pending,
      Confirmed,
      Canceled
    }

    default ShoppingCartStatus status() {
      return switch (this) {
        case PendingShoppingCart ignored:
          yield ShoppingCartStatus.Pending;
        case ConfirmedShoppingCart ignored:
          yield ShoppingCartStatus.Confirmed;
        case CanceledShoppingCart ignored:
          yield ShoppingCartStatus.Canceled;
      };
    }

    default boolean isClosed() {
      return this instanceof ConfirmedShoppingCart || this instanceof CanceledShoppingCart;
    }

    static ShoppingCart when(ShoppingCart current, ShoppingCartEvent event) {
      return switch (event) {
        case ShoppingCartOpened shoppingCartOpened:
          yield new PendingShoppingCart(
            shoppingCartOpened.shoppingCartId(),
            shoppingCartOpened.clientId(),
            ProductItems.empty()
          );
        case ProductItemAddedToShoppingCart productItemAddedToShoppingCart:
          yield new PendingShoppingCart(
            current.id(),
            current.clientId(),
            current.productItems().add(productItemAddedToShoppingCart.productItem())
          );
        case ProductItemRemovedFromShoppingCart productItemRemovedFromShoppingCart:
          yield new PendingShoppingCart(
            current.id(),
            current.clientId(),
            current.productItems().remove(productItemRemovedFromShoppingCart.productItem())
          );
        case ShoppingCartConfirmed shoppingCartConfirmed:
          yield new ConfirmedShoppingCart(
            current.id(),
            current.clientId(),
            current.productItems(),
            shoppingCartConfirmed.confirmedAt()
          );
        case ShoppingCartCanceled shoppingCartCanceled:
          yield new CanceledShoppingCart(
            current.id(),
            current.clientId(),
            current.productItems(),
            shoppingCartCanceled.canceledAt()
          );
      };
    }

    static ShoppingCart empty() {
      return new PendingShoppingCart(null, null, null);
    }
  }

  record ProductItem(UUID productId, int quantity) {
  }

  record PricedProductItem(UUID productId, int quantity, double unitPrice) {
    public double totalAmount() {
      return quantity * unitPrice;
    }
  }

  record ProductItems(PricedProductItem[] values) {
    public static ProductItems empty() {
      return new ProductItems(new PricedProductItem[]{});
    }


    public ProductItems add(PricedProductItem productItem) {
      // Note that we cannot simply group by product item id since the order of entries().stream().is arbitrary.
      boolean containsProduct = Arrays.stream(values).anyMatch(pricedProductItem -> pricedProductItem.productId.equals(productItem.productId));
      final Stream<PricedProductItem> resultingStream;
      if (containsProduct) {
        resultingStream = Arrays.stream(values)
          .map(pricedProductItem -> new PricedProductItem(
            pricedProductItem.productId,
            pricedProductItem.quantity + productItem.quantity,
            pricedProductItem.unitPrice));
      } else {
        resultingStream = StreamEx.of(values).append(productItem);
      }
      return new ProductItems(resultingStream.toArray(PricedProductItem[]::new));
    }

    public ProductItems remove(PricedProductItem productItem) {
      return new ProductItems(
        Arrays.stream(values())
          .map(pi -> pi.productId().equals(productItem.productId()) ?
            new PricedProductItem(
              pi.productId(),
              pi.quantity() - productItem.quantity(),
              pi.unitPrice()
            )
            : pi
          )
          .filter(pi -> pi.quantity() > 0)
          .toArray(PricedProductItem[]::new)
      );
    }

    public boolean hasEnough(PricedProductItem productItem) {
      var currentQuantity = Arrays.stream(values)
        .filter(pi -> pi.productId().equals(productItem.productId()))
        .mapToInt(PricedProductItem::quantity)
        .sum();

      return currentQuantity >= productItem.quantity();
    }
  }
}

