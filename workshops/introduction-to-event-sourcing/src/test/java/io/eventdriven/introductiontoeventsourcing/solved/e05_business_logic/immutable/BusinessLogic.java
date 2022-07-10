package io.eventdriven.introductiontoeventsourcing.solved.e05_business_logic.immutable;

import io.eventdriven.introductiontoeventsourcing.solved.e05_business_logic.immutable.BusinessLogicTests.PricedProductItem;
import io.eventdriven.introductiontoeventsourcing.solved.e05_business_logic.immutable.BusinessLogicTests.ShoppingCart;
import io.eventdriven.introductiontoeventsourcing.v1.productitems.ProductItem;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.solved.e05_business_logic.immutable.BusinessLogicTests.ShoppingCartEvent.*;

public class BusinessLogic {
  public record OpenShoppingCart(
    UUID shoppingCartId,
    UUID clientId
  ) {
    public static ShoppingCartOpened handle(OpenShoppingCart command) {
      return new ShoppingCartOpened(
        command.shoppingCartId(),
        command.clientId()
      );
    }
  }

  public record AddProductItemToShoppingCart(
    UUID shoppingCartId,
    ProductItem productItem
  ) {
    public static ProductItemAddedToShoppingCart handle(
      ProductPriceCalculator productPriceCalculator,
      AddProductItemToShoppingCart command,
      ShoppingCart shoppingCart
    ) {
      if (shoppingCart.isClosed())
        throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

      var pricedProductItem = productPriceCalculator.calculate(command.productItem);

      shoppingCart.productItems().add(pricedProductItem);

      return new ProductItemAddedToShoppingCart(
        command.shoppingCartId,
        pricedProductItem
      );
    }
  }

  public record RemoveProductItemFromShoppingCart(
    UUID shoppingCartId,
    PricedProductItem productItem
  ) {
    public static ProductItemRemovedFromShoppingCart handle(
      RemoveProductItemFromShoppingCart command,
      ShoppingCart shoppingCart
    ) {
      if (shoppingCart.isClosed())
        throw new IllegalStateException("Adding product item for cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

      shoppingCart.productItems().hasEnough(command.productItem());

      return new ProductItemRemovedFromShoppingCart(
        command.shoppingCartId(),
        command.productItem()
      );
    }
  }

  public record ConfirmShoppingCart(
    UUID shoppingCartId
  ) {
    public static ShoppingCartConfirmed handle(ConfirmShoppingCart command, ShoppingCart shoppingCart) {
      if (shoppingCart.isClosed())
        throw new IllegalStateException("Confirming cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

      return new ShoppingCartConfirmed(
        shoppingCart.id(),
        OffsetDateTime.now()
      );
    }
  }

  public record CancelShoppingCart(
    UUID shoppingCartId
  ) {
    public static ShoppingCartCanceled handle(CancelShoppingCart command, ShoppingCart shoppingCart) {
      if (shoppingCart.isClosed())
        throw new IllegalStateException("Canceling cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

      return new ShoppingCartCanceled(
        shoppingCart.id(),
        OffsetDateTime.now()
      );
    }
  }

  public interface ProductPriceCalculator
  {
    PricedProductItem calculate(ProductItem productItems);
  }

  public static class FakeProductPriceCalculator implements ProductPriceCalculator
  {
    private final double value;

    private FakeProductPriceCalculator(double value)
    {
      this.value = value;
    }

    public static FakeProductPriceCalculator returning(double value)
    {
      return new FakeProductPriceCalculator(value);
    }

    public PricedProductItem calculate(ProductItem productItem)
    {
      return new PricedProductItem(productItem.productId(), productItem.quantity(), value);
    }
  }
}
