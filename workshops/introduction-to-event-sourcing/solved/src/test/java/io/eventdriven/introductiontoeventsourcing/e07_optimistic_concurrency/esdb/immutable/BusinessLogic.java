package io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable;

import io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable.OptimisticConcurrencyTests.*;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Supplier;

import static io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable.BusinessLogic.ShoppingCartCommand.*;
import static io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable.OptimisticConcurrencyTests.ShoppingCartEvent.*;

public class BusinessLogic {
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

  public static class ShoppingCartCommandHandler {
    public static ShoppingCartEvent decide(
      Supplier<ProductPriceCalculator> getProductPriceCalculator,
      ShoppingCartCommand command,
      ShoppingCart entity
    ) {
      return switch (command) {
        case OpenShoppingCart openCommand ->
          open(openCommand);
        case AddProductItemToShoppingCart addCommand ->
          addProductItem(getProductPriceCalculator.get(), addCommand, entity);
        case RemoveProductItemFromShoppingCart removeProductCommand ->
          removeProductItem(removeProductCommand, entity);
        case ConfirmShoppingCart confirmCommand ->
          confirm(confirmCommand, entity);
        case CancelShoppingCart cancelCommand ->
          cancel(cancelCommand, entity);
      };
    }

    public static ShoppingCartOpened open(OpenShoppingCart command) {
      return new ShoppingCartOpened(
        command.shoppingCartId(),
        command.clientId()
      );
    }

    public static ProductItemAddedToShoppingCart addProductItem(
      ProductPriceCalculator productPriceCalculator,
      AddProductItemToShoppingCart command,
      ShoppingCart shoppingCart
    ) {
      if (shoppingCart.isClosed())
        throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

      var pricedProductItem = productPriceCalculator.calculate(command.productItem());

      shoppingCart.productItems().add(pricedProductItem);

      return new ProductItemAddedToShoppingCart(
        command.shoppingCartId(),
        pricedProductItem
      );
    }

    public static ProductItemRemovedFromShoppingCart removeProductItem(
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

    public static ShoppingCartConfirmed confirm(ConfirmShoppingCart command, ShoppingCart shoppingCart) {
      if (shoppingCart.isClosed())
        throw new IllegalStateException("Confirming cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

      return new ShoppingCartConfirmed(
        shoppingCart.id(),
        OffsetDateTime.now()
      );
    }

    public static ShoppingCartCanceled cancel(CancelShoppingCart command, ShoppingCart shoppingCart) {
      if (shoppingCart.isClosed())
        throw new IllegalStateException("Canceling cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

      return new ShoppingCartCanceled(
        shoppingCart.id(),
        OffsetDateTime.now()
      );
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
