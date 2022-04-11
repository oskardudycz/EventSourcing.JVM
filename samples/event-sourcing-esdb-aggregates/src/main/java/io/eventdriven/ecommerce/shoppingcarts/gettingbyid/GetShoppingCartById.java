package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;

import io.eventdriven.ecommerce.core.http.ETag;
import org.springframework.lang.Nullable;

import java.util.Optional;
import java.util.UUID;

public record GetShoppingCartById(
  UUID shoppingCartId,
  @Nullable ETag eTag
) {
  public static Optional<ShoppingCartDetails> handle(
    ShoppingCartDetailsRepository repository,
    GetShoppingCartById query
  ) {
    return query.eTag() == null ?
      repository.findById(query.shoppingCartId())
      : repository.findByIdAndNeverVersion(query.shoppingCartId(), query.eTag().toLong());
  }
}
