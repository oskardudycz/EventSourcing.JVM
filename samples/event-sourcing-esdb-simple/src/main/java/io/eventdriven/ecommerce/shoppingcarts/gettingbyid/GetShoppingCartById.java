package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;


import io.eventdriven.ecommerce.core.http.ETag;
import org.springframework.lang.Nullable;
import org.springframework.retry.support.RetryTemplate;

import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;

public record GetShoppingCartById(
  UUID shoppingCartId,
  @Nullable ETag eTag
) {
  public static ShoppingCartDetails handle(
    ShoppingCartDetailsRepository repository,
    GetShoppingCartById query
  ) {
    // example of long-polling
    RetryTemplate retryTemplate = RetryTemplate.builder()
      .retryOn(EntityNotFoundException.class)
      .exponentialBackoff(100, 2, 1000)
      .withinMillis(5000)
      .build();

    return retryTemplate.execute(context -> {
      var result = query.eTag() == null ?
        repository.findById(query.shoppingCartId())
        : repository.findByIdAndNeverVersion(query.shoppingCartId(), query.eTag().toLong());

      if (result.isEmpty()) {
        throw new EntityNotFoundException("Shopping cart not found");
      }

      return result.get();
    });
  }
}
