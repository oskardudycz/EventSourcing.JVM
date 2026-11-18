package io.eventdriven.ecommerce.shoppingcarts.gettingcarts;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;

public record GetShoppingCarts(int pageNumber, int pageSize) {
    public GetShoppingCarts {
        if (pageNumber < 0) throw new IllegalArgumentException("Page number has to be a zero-based number");

        if (pageSize < 0) throw new IllegalArgumentException("Page size has to be a zero-based number");
    }

    public static GetShoppingCarts of(@Nullable Integer pageNumber, @Nullable Integer pageSize) {

        return new GetShoppingCarts(pageNumber != null ? pageNumber : 0, pageSize != null ? pageSize : 20);
    }

    public static List<ShoppingCartShortInfo> handle(
            ShoppingCartShortInfoRepository repository, GetShoppingCarts query) {
        return repository.findAll(PageRequest.of(query.pageNumber(), query.pageSize())).stream()
                .toList();
    }
}
