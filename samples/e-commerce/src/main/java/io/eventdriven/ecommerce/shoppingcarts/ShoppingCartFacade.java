package io.eventdriven.ecommerce.shoppingcarts;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static java.util.stream.Collectors.toMap;

import io.eventdriven.core.commands.CommandHandler;
import io.eventdriven.ecommerce.shoppingcarts.currentcart.CustomerShoppingCarts;
import io.eventdriven.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class ShoppingCartFacade {
    private final CommandHandler<ShoppingCart.Id, ShoppingCart, ShoppingCartEvent> commandHandler;
    private final CustomerShoppingCarts customerShoppingCarts;
    private final ProductPriceCalculator prices;
    private final Supplier<OffsetDateTime> now;

    public static String mapToStreamId(ShoppingCart.Id shoppingCartId) {
        return "ShoppingCart-%s".formatted(shoppingCartId.tail());
    }

    public ShoppingCartFacade(
            CommandHandler<ShoppingCart.Id, ShoppingCart, ShoppingCartEvent> commandHandler,
            CustomerShoppingCarts customerShoppingCarts,
            ProductPriceCalculator prices,
            Supplier<OffsetDateTime> now) {
        this.commandHandler = commandHandler;
        this.customerShoppingCarts = customerShoppingCarts;
        this.prices = prices;
        this.now = now;
    }

    public long addProductItem(UUID customerId, ProductItem productItem) {
        var shoppingCartId = customerShoppingCarts
                .currentOf(customerId)
                .orElseGet(() -> ShoppingCart.Id.of(customerId, customerShoppingCarts.countOf(customerId) + 1));
        var command = new AddProductItemToShoppingCart(
                shoppingCartId,
                customerId,
                productItem,
                prices.calculate(productItem).unitPrice(),
                now.get());

        return commandHandler.handle(shoppingCartId, command, ShoppingCartDecider::addProductItem);
    }

    public long removeProductItem(RemoveProductItemFromShoppingCart request) {
        var command = new RemoveProductItemFromShoppingCart(
                request.shoppingCartId(), request.productItem(), request.expectedVersion(), now.get());

        return commandHandler.handle(
                command.shoppingCartId(), command.expectedVersion(), command, ShoppingCartDecider::removeProductItem);
    }

    public long checkOut(CheckOutShoppingCart request) {
        var checkedOutAt = now.get();

        return commandHandler.handle(
                request.shoppingCartId(),
                request.expectedVersion(),
                request,
                (command, state) -> ShoppingCartDecider.checkOut(
                        new CheckOutShoppingCart(
                                command.shoppingCartId(),
                                command.expectedVersion(),
                                currentUnitPricesOf(state),
                                checkedOutAt),
                        state));
    }

    public boolean isCheckedOut(ShoppingCart.Id shoppingCartId) {
        return commandHandler.get(shoppingCartId).isCheckedOut();
    }

    private Map<UUID, Double> currentUnitPricesOf(ShoppingCart state) {
        return state.productItems().items().stream()
                .map(PricedProductItem::productItem)
                .collect(toMap(
                        ProductItem::productId, item -> prices.calculate(item).unitPrice()));
    }
}
