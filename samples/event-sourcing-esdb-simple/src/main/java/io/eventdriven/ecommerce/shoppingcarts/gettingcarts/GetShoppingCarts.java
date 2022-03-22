package io.eventdriven.ecommerce.shoppingcarts.gettingcarts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

public record GetShoppingCarts(
  int pageNumber,
  int pageSize
) {
  public static GetShoppingCarts from(Optional<Integer> pageNumber, Optional<Integer> pageSize) {
    if (!pageNumber.isEmpty() && pageNumber.get() < 0)
      throw new IllegalArgumentException("Page number has to be a zero-based number");

    if (!pageSize.isEmpty() && pageSize.get() < 0)
      throw new IllegalArgumentException("Page size has to be a zero-based number");

    return new GetShoppingCarts(pageNumber.orElse(0), pageSize.orElse(20));
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
