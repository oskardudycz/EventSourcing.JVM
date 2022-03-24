package io.eventdriven.ecommerce.shoppingcarts.gettingcarts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;

public record GetShoppingCarts(
  int pageNumber,
  int pageSize
) {
  public GetShoppingCarts {
    if (pageNumber < 0)
      throw new IllegalArgumentException("Page number has to be a zero-based number");

    if (pageSize < 0)
      throw new IllegalArgumentException("Page size has to be a zero-based number");
  }

  public static GetShoppingCarts of(@Nullable Integer pageNumber, @Nullable Integer pageSize) {

    return new GetShoppingCarts(
      pageNumber != null ? pageNumber : 0,
      pageSize != null ? pageSize : 20
    );
  }

  public static Page<ShoppingCartShortInfo> handle(
    ShoppingCartShortInfoRepository repository,
    GetShoppingCarts query
  ) {
    return repository.findAll(
      PageRequest.of(query.pageNumber(), query.pageSize())
    );
  }
}
