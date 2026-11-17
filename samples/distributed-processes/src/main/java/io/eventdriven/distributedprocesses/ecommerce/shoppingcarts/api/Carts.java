package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api;

import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.core.projections.Projection;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItems;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

public class Carts extends Projection<Cart> {
  public Carts(Database database) {
    super(Cart.class, database);
  }

  @Override
  public List<Class<?>> canHandle() {
    return List.of(
      ProductItemAddedToShoppingCart.class,
      ProductItemRemovedFromShoppingCart.class,
      ProductItemPriceChanged.class,
      ShoppingCartCheckedOut.class
    );
  }

  @Override
  protected EntityId getDocumentId(Object event) {
    return switch (event) {
      case ProductItemAddedToShoppingCart e -> e.shoppingCartId();
      case ProductItemRemovedFromShoppingCart e -> e.shoppingCartId();
      case ProductItemPriceChanged e -> e.shoppingCartId();
      case ShoppingCartCheckedOut e -> e.shoppingCartId();
      default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
    };
  }

  @Override
  protected @Nullable Cart evolve(@Nullable Cart current, Object event) {
    return switch (event) {
      case ProductItemAddedToShoppingCart e -> current == null
        ? cartWith(e.shoppingCartId(), e.customerId(), ProductItems.empty().add(e.productItem()), false)
        : cartWith(current, productItemsOf(current).add(e.productItem()));
      case ProductItemRemovedFromShoppingCart e -> cartWith(current, productItemsOf(current).remove(e.productItem()));
      case ProductItemPriceChanged e ->
        cartWith(current, productItemsOf(current).changeUnitPrice(e.productId(), e.newUnitPrice()));
      case ShoppingCartCheckedOut _ ->
        cartWith(current.getShoppingCartId(), current.getCustomerId(), productItemsOf(current), true);
      default -> current;
    };
  }

  private static Cart cartWith(Cart current, ProductItems productItems) {
    return cartWith(current.getShoppingCartId(), current.getCustomerId(), productItems, current.isCheckedOut());
  }

  private static Cart cartWith(
    ShoppingCartId shoppingCartId,
    UUID customerId,
    ProductItems productItems,
    boolean checkedOut
  ) {
    var lines = productItems.items().stream()
      .map(item -> new Cart.Line(item.productId(), item.quantity(), item.unitPrice()))
      .toList();

    return new Cart(
      shoppingCartId,
      customerId,
      lines,
      productItems.items().stream().mapToDouble(PricedProductItem::totalPrice).sum(),
      checkedOut
    );
  }

  private static ProductItems productItemsOf(Cart cart) {
    return new ProductItems(new ArrayList<>(cart.getLines().stream()
      .map(line -> new PricedProductItem(new ProductItem(line.productId(), line.quantity()), line.unitPrice()))
      .toList()));
  }
}
