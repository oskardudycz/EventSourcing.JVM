package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;


import io.eventdriven.ecommerce.core.http.ETag;
import org.jspecify.annotations.Nullable;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
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
    RetryTemplate retryTemplate = new RetryTemplate(
      RetryPolicy.builder()
        .includes(EntityNotFoundException.class)
        .maxRetries(Long.MAX_VALUE)
        .delay(Duration.ofMillis(100))
        .multiplier(2)
        .maxDelay(Duration.ofMillis(1000))
        .timeout(Duration.ofMillis(5000))
        .build()
    );

    return retryTemplate.invoke(() -> {
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
