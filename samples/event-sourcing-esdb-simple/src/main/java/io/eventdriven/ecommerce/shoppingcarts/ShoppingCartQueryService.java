package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.core.entities.CommandHandler;
import io.eventdriven.ecommerce.core.http.ETag;
import io.eventdriven.ecommerce.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.GetShoppingCartById;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetails;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetailsRepository;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.GetShoppingCarts;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.ShoppingCartShortInfo;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.ShoppingCartShortInfoRepository;
import org.springframework.data.domain.Page;
import org.springframework.retry.support.RetryTemplate;

import javax.persistence.EntityNotFoundException;

public class ShoppingCartQueryService {
  private final ShoppingCartDetailsRepository detailsRepository;
  private final ShoppingCartShortInfoRepository shortInfoRepository;

  public ShoppingCartQueryService(
    ShoppingCartDetailsRepository detailsRepository,
    ShoppingCartShortInfoRepository shortInfoRepository
  ) {
    this.detailsRepository = detailsRepository;
    this.shortInfoRepository = shortInfoRepository;
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

      if (result.isEmpty()) {
        throw new EntityNotFoundException("Shopping cart not found");
      }

      return result.get();
    });
  }

  public Page<ShoppingCartShortInfo> getShoppingCarts(GetShoppingCarts query) {
    return GetShoppingCarts.handle(shortInfoRepository, query);
  }
}
