package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.core.aggregates.AggregateStore;
import io.eventdriven.ecommerce.core.http.ETag;
import io.eventdriven.ecommerce.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.AddProductItemToShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.CancelShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.ConfirmShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.GetShoppingCartById;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetails;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetailsRepository;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.GetShoppingCarts;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.ShoppingCartShortInfo;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.ShoppingCartShortInfoRepository;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.OpenShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.RemoveProductItemFromShoppingCart;
import org.springframework.data.domain.Page;
import org.springframework.retry.support.RetryTemplate;

import javax.persistence.EntityNotFoundException;
import java.util.UUID;

public class ShoppingCartService {
  private final AggregateStore<ShoppingCart, ShoppingCartEvent, UUID> store;
  private final ShoppingCartDetailsRepository detailsRepository;
  private final ShoppingCartShortInfoRepository shortInfoRepository;
  private final ProductPriceCalculator productPriceCalculator;

  public ShoppingCartService(
    AggregateStore<ShoppingCart, ShoppingCartEvent, UUID> entityStore,
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
      () -> ShoppingCart.open(command.shoppingCartId(), command.clientId())
    );
  }

  public ETag addProductItem(AddProductItemToShoppingCart command) {
    return store.getAndUpdate(
      current -> current.addProductItem(productPriceCalculator, command.productItem()),
      command.shoppingCartId(),
      command.expectedVersion()
    );
  }

  public ETag removeProductItem(RemoveProductItemFromShoppingCart command) {
    return store.getAndUpdate(
      current -> current.removeProductItem(command.productItem()),
      command.shoppingCartId(),
      command.expectedVersion()
    );
  }

  public ETag confirm(ConfirmShoppingCart command) {
    return store.getAndUpdate(
      current -> current.confirm(),
      command.shoppingCartId(),
      command.expectedVersion()
    );
  }

  public ETag cancel(CancelShoppingCart command) {
    return store.getAndUpdate(
      current -> current.cancel(),
      command.shoppingCartId(),
      command.expectedVersion()
    );
  }

  public ShoppingCartDetails getById(GetShoppingCartById query) {
    // example of long-polling
    RetryTemplate retryTemplate = RetryTemplate.builder()
      .retryOn(EntityNotFoundException.class)
      .exponentialBackoff(100, 2, 1000)
      .withinMillis(5000)
      .build();

    return retryTemplate.execute(context -> {
      var result = GetShoppingCartById.handle(detailsRepository, query);

      if(result.isEmpty()){
        throw new EntityNotFoundException("Shopping cart not found");
      }

      return result.get();
    });
  }

  public Page<ShoppingCartShortInfo> getShoppingCarts(GetShoppingCarts query) {
    return GetShoppingCarts.handle(shortInfoRepository, query);
  }
}
