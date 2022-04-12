package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.core.aggregates.AggregateStore;
import io.eventdriven.ecommerce.core.http.ETag;
import io.eventdriven.ecommerce.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.GetShoppingCartById;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetails;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetailsRepository;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.GetShoppingCarts;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.ShoppingCartShortInfo;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.ShoppingCartShortInfoRepository;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
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

  public ETag open(UUID shoppingCartId, UUID clientId) {
    return store.add(ShoppingCart.open(shoppingCartId, clientId));
  }

  public ETag addProductItem(UUID shoppingCartId, ProductItem productItem, Long expectedVersion) {
    return store.getAndUpdate(
      current -> current.addProductItem(productPriceCalculator, productItem),
      shoppingCartId,
      expectedVersion
    );
  }

  public ETag removeProductItem(UUID shoppingCartId, PricedProductItem productItem, Long expectedVersion) {
    return store.getAndUpdate(
      current -> current.removeProductItem(productItem),
      shoppingCartId,
      expectedVersion
    );
  }

  public ETag confirm(UUID shoppingCartId, Long expectedVersion) {
    return store.getAndUpdate(
      current -> current.confirm(),
      shoppingCartId,
      expectedVersion
    );
  }

  public ETag cancel(UUID shoppingCartId, Long expectedVersion) {
    return store.getAndUpdate(
      current -> current.cancel(),
      shoppingCartId,
      expectedVersion
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
