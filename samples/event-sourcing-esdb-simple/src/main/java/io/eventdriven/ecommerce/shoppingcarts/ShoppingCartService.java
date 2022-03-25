package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.core.entities.EntityStore;
import io.eventdriven.ecommerce.core.http.ETag;
import io.eventdriven.ecommerce.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.addingproductitem.AddProductItemToShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.canceling.CancelShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.confirming.ConfirmShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.GetShoppingCartById;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetails;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetailsRepository;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.GetShoppingCarts;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.ShoppingCartShortInfo;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.ShoppingCartShortInfoRepository;
import io.eventdriven.ecommerce.shoppingcarts.opening.OpenShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.removingproductitem.RemoveProductItemFromShoppingCart;
import org.springframework.data.domain.Page;

import java.util.Optional;

public class ShoppingCartService {
  private final EntityStore<ShoppingCart, ShoppingCartEvent> store;
  private final ShoppingCartDetailsRepository detailsRepository;
  private final ShoppingCartShortInfoRepository shortInfoRepository;
  private final ProductPriceCalculator productPriceCalculator;

  public ShoppingCartService(
    EntityStore<ShoppingCart, ShoppingCartEvent> entityStore,
    ShoppingCartDetailsRepository detailsRepository,
    ShoppingCartShortInfoRepository shortInfoRepository,
    ProductPriceCalculator productPriceCalculator) {
    this.store = entityStore;
    this.detailsRepository = detailsRepository;
    this.shortInfoRepository = shortInfoRepository;
    this.productPriceCalculator = productPriceCalculator;
  }

  public ETag open(OpenShoppingCart command) {
    return store.add(
      () -> OpenShoppingCart.handle(command),
      command.shoppingCartId()
    );
  }

  public ETag addProductItem(AddProductItemToShoppingCart command) {
    return store.getAndUpdate(
      current -> AddProductItemToShoppingCart.handle(productPriceCalculator, command, current),
      command.shoppingCartId(),
      command.expectedVersion()
    );
  }

  public ETag removeProductItem(RemoveProductItemFromShoppingCart command) {
    return store.getAndUpdate(
      current -> RemoveProductItemFromShoppingCart.handle(command, current),
      command.shoppingCartId(),
      command.expectedVersion()
    );
  }

  public ETag confirm(ConfirmShoppingCart command) {
    return store.getAndUpdate(
      current -> ConfirmShoppingCart.handle(command, current),
      command.shoppingCartId(),
      command.expectedVersion()
    );
  }

  public ETag cancel(CancelShoppingCart command) {
    return store.getAndUpdate(
      current -> CancelShoppingCart.handle(command, current),
      command.shoppingCartId(),
      command.expectedVersion()
    );
  }

  public Optional<ShoppingCartDetails> getById(GetShoppingCartById query) {
    return GetShoppingCartById.handle(detailsRepository, query);
  }

  public Page<ShoppingCartShortInfo> getShoppingCarts(GetShoppingCarts query) {
    return GetShoppingCarts.handle(shortInfoRepository, query);
  }
}
