package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;

import io.eventdriven.ecommerce.core.http.ETag;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record GetShoppingCartById(
        UUID shoppingCartId, @Nullable ETag eTag) {
    public static Optional<ShoppingCartDetails> handle(
            ShoppingCartDetailsRepository repository, GetShoppingCartById query) {
        return query.eTag() == null
                ? repository.findById(query.shoppingCartId())
                : repository.findByIdAndNeverVersion(
                        query.shoppingCartId(), query.eTag().toLong());
    }
}
