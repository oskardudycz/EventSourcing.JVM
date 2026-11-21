package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.EventSourcedSpecification;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ShoppingCartTests extends EventSourcedSpecification<ShoppingCart, ShoppingCartEvent> {
    private final UUID customerId = UUID.randomUUID();
    private final ShoppingCartId shoppingCartId = ShoppingCartId.of(customerId, 1);
    private final ProductItem shoes = new ProductItem(UUID.randomUUID(), 2);
    private final ProductItem shirt = new ProductItem(UUID.randomUUID(), 1);
    private final Map<UUID, Double> unitPrices = new HashMap<>(Map.of(shoes.productId(), 10.0, shirt.productId(), 5.0));
    private final ProductPriceCalculator prices = item -> new PricedProductItem(item, unitPrices.get(item.productId()));
    private final OffsetDateTime now = OffsetDateTime.now();
    private final ShoppingCartDecider decider = new ShoppingCartDecider(prices, () -> now);

    protected ShoppingCartTests() {
        super(ShoppingCart::initial, ShoppingCart::evolve);
    }

    @Test
    public void theFirstProductStartsTheCart() {
        given().when(current ->
                        decider.handle(new AddProductItemToShoppingCart(shoppingCartId, customerId, shoes), current))
                .then(new ProductItemAddedToShoppingCart(
                        shoppingCartId, customerId, new PricedProductItem(shoes, 10), now));
    }

    @Test
    public void addingAProductThatIsAlreadyInTheCartKeepsTheUnitPriceOfItsLine() {
        unitPrices.put(shoes.productId(), 12.0);

        given(added(shoes, 10))
                .when(current ->
                        decider.handle(new AddProductItemToShoppingCart(shoppingCartId, customerId, shoes), current))
                .then(new ProductItemAddedToShoppingCart(
                        shoppingCartId, customerId, new PricedProductItem(shoes, 10), now));
    }

    @Test
    public void removingAProductRecordsItAtTheUnitPriceOfItsLine() {
        var oneShoe = new ProductItem(shoes.productId(), 1);

        given(added(shoes, 10))
                .when(current ->
                        decider.handle(new RemoveProductItemFromShoppingCart(shoppingCartId, oneShoe, 0L), current))
                .then(new ProductItemRemovedFromShoppingCart(shoppingCartId, new PricedProductItem(oneShoe, 10), now));
    }

    @Test
    public void removingMoreThanTheCartHoldsThrows() {
        given(added(shoes, 10))
                .when(current -> decider.handle(
                        new RemoveProductItemFromShoppingCart(
                                shoppingCartId, new ProductItem(shoes.productId(), 3), 0L),
                        current))
                .thenThrows(IllegalStateException.class);
    }

    @Test
    public void removingAProductThatIsNotInTheCartThrows() {
        given(added(shoes, 10))
                .when(current ->
                        decider.handle(new RemoveProductItemFromShoppingCart(shoppingCartId, shirt, 0L), current))
                .thenThrows(IllegalStateException.class);
    }

    @Test
    public void checkingOutWithUnchangedPricesChecksTheCartOut() {
        given(added(shoes, 10), added(shirt, 5))
                .when(current -> decider.handle(new CheckOutShoppingCart(shoppingCartId, 0L), current))
                .then(new ShoppingCartCheckedOut(shoppingCartId, now));
    }

    @Test
    public void checkingOutAfterAPriceChangedRecordsTheChangeInsteadOfCheckingOut() {
        unitPrices.put(shoes.productId(), 12.0);

        given(added(shoes, 10), added(shirt, 5))
                .when(current -> decider.handle(new CheckOutShoppingCart(shoppingCartId, 0L), current))
                .then(new ProductItemPriceChanged(shoppingCartId, shoes.productId(), 10, 12, now));
    }

    @Test
    public void checkingOutAgainAtTheNewPriceChecksTheCartOut() {
        unitPrices.put(shoes.productId(), 12.0);

        given(added(shoes, 10), new ProductItemPriceChanged(shoppingCartId, shoes.productId(), 10, 12, now))
                .when(current -> decider.handle(new CheckOutShoppingCart(shoppingCartId, 0L), current))
                .then(new ShoppingCartCheckedOut(shoppingCartId, now));
    }

    @Test
    public void checkingOutAnEmptyCartThrows() {
        given(
                        added(shoes, 10),
                        new ProductItemRemovedFromShoppingCart(shoppingCartId, new PricedProductItem(shoes, 10), now))
                .when(current -> decider.handle(new CheckOutShoppingCart(shoppingCartId, 0L), current))
                .thenThrows(IllegalStateException.class);
    }

    @Test
    public void checkingOutACartThatDoesNotExistThrows() {
        given().when(current -> decider.handle(new CheckOutShoppingCart(shoppingCartId, 0L), current))
                .thenThrows(IllegalStateException.class);
    }

    @Test
    public void checkingOutACheckedOutCartAgainRecordsNothing() {
        given(added(shoes, 10), new ShoppingCartCheckedOut(shoppingCartId, now))
                .when(current -> decider.handle(new CheckOutShoppingCart(shoppingCartId, 0L), current))
                .thenNothing();
    }

    @Test
    public void addingToACheckedOutCartThrows() {
        given(added(shoes, 10), new ShoppingCartCheckedOut(shoppingCartId, now))
                .when(current ->
                        decider.handle(new AddProductItemToShoppingCart(shoppingCartId, customerId, shirt), current))
                .thenThrows(IllegalStateException.class);
    }

    private ProductItemAddedToShoppingCart added(ProductItem productItem, double unitPrice) {
        return new ProductItemAddedToShoppingCart(
                shoppingCartId, customerId, new PricedProductItem(productItem, unitPrice), now);
    }
}
