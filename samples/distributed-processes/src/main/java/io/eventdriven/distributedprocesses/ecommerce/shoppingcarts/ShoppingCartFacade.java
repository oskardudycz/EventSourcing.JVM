package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;

import io.eventdriven.distributedprocesses.core.commands.CommandHandler;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import java.util.UUID;

public class ShoppingCartFacade {
    private final CommandHandler<ShoppingCartId, ShoppingCart, ShoppingCartCommand, ShoppingCartEvent> commandHandler;
    private final CustomerShoppingCarts customerShoppingCarts;

    public static String mapToStreamId(ShoppingCartId shoppingCartId) {
        return "ShoppingCart-%s".formatted(shoppingCartId.tail());
    }

    public ShoppingCartFacade(
            CommandHandler<ShoppingCartId, ShoppingCart, ShoppingCartCommand, ShoppingCartEvent> commandHandler,
            CustomerShoppingCarts customerShoppingCarts) {
        this.commandHandler = commandHandler;
        this.customerShoppingCarts = customerShoppingCarts;
    }

    public long addProductItem(UUID customerId, ProductItem productItem) {
        var shoppingCartId = customerShoppingCarts
                .currentOf(customerId)
                .orElseGet(() -> ShoppingCartId.of(customerId, customerShoppingCarts.countOf(customerId) + 1));

        return commandHandler.handle(
                shoppingCartId, new AddProductItemToShoppingCart(shoppingCartId, customerId, productItem));
    }

    public long removeProductItem(RemoveProductItemFromShoppingCart command) {
        return commandHandler.handle(command.shoppingCartId(), command, command.expectedVersion());
    }

    public long checkOut(CheckOutShoppingCart command) {
        return commandHandler.handle(command.shoppingCartId(), command, command.expectedVersion());
    }

    public boolean isCheckedOut(ShoppingCartId shoppingCartId) {
        return commandHandler.get(shoppingCartId).isCheckedOut();
    }
}
