package io.eventdriven.ecommerce.shoppingcarts;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartDecider.decide;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;

import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.eventsourcing.EventSourcedSpecification;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ShoppingCartTests extends EventSourcedSpecification<ShoppingCart, ShoppingCartEvent> {
    private final UUID customerId = UUID.randomUUID();
    private final ShoppingCart.Id shoppingCartId = ShoppingCart.Id.of(customerId, 1);
    private final ProductItem shoes = new ProductItem(UUID.randomUUID(), 2);
    private final ProductItem shirt = new ProductItem(UUID.randomUUID(), 1);
    private final OffsetDateTime now = OffsetDateTime.now();

    protected ShoppingCartTests() {
        super(ShoppingCart::initial, ShoppingCart::evolve);
    }

    @Test
    public void theFirstProductStartsTheCart() {
        given().when(current -> decide(add(shoes, 10), current))
                .then(new ProductItemAddedToShoppingCart(
                        shoppingCartId, customerId, new PricedProductItem(shoes, 10), now));
    }

    @Test
    public void addingAProductThatIsAlreadyInTheCartKeepsTheUnitPriceOfItsLine() {
        given(added(shoes, 10))
                .when(current -> decide(add(shoes, 12), current))
                .then(new ProductItemAddedToShoppingCart(
                        shoppingCartId, customerId, new PricedProductItem(shoes, 10), now));
    }

    @Test
    public void removingAProductRecordsItAtTheUnitPriceOfItsLine() {
        var oneShoe = new ProductItem(shoes.productId(), 1);

        given(added(shoes, 10))
                .when(current -> decide(remove(oneShoe), current))
                .then(new ProductItemRemovedFromShoppingCart(shoppingCartId, new PricedProductItem(oneShoe, 10), now));
    }

    @Test
    public void removingMoreThanTheCartHoldsThrows() {
        given(added(shoes, 10))
                .when(current -> decide(remove(new ProductItem(shoes.productId(), 3)), current))
                .thenThrows(IllegalStateException.class);
    }

    @Test
    public void removingAProductThatIsNotInTheCartThrows() {
        given(added(shoes, 10)).when(current -> decide(remove(shirt), current)).thenThrows(IllegalStateException.class);
    }

    @Test
    public void checkingOutWithUnchangedPricesChecksTheCartOut() {
        given(added(shoes, 10), added(shirt, 5))
                .when(current -> decide(checkOut(Map.of(shoes.productId(), 10.0, shirt.productId(), 5.0)), current))
                .then(new ShoppingCartCheckedOut(shoppingCartId, now));
    }

    @Test
    public void checkingOutAfterAPriceChangedRecordsTheChangeInsteadOfCheckingOut() {
        given(added(shoes, 10), added(shirt, 5))
                .when(current -> decide(checkOut(Map.of(shoes.productId(), 12.0, shirt.productId(), 5.0)), current))
                .then(new ProductItemPriceChanged(shoppingCartId, shoes.productId(), 10, 12, now));
    }

    @Test
    public void checkingOutAgainAtTheNewPriceChecksTheCartOut() {
        given(added(shoes, 10), new ProductItemPriceChanged(shoppingCartId, shoes.productId(), 10, 12, now))
                .when(current -> decide(checkOut(Map.of(shoes.productId(), 12.0)), current))
                .then(new ShoppingCartCheckedOut(shoppingCartId, now));
    }

    @Test
    public void checkingOutAnEmptyCartThrows() {
        given(
                        added(shoes, 10),
                        new ProductItemRemovedFromShoppingCart(shoppingCartId, new PricedProductItem(shoes, 10), now))
                .when(current -> decide(checkOut(Map.of()), current))
                .thenThrows(IllegalStateException.class);
    }

    @Test
    public void checkingOutACartThatDoesNotExistThrows() {
        given().when(current -> decide(checkOut(Map.of()), current)).thenThrows(IllegalStateException.class);
    }

    @Test
    public void checkingOutACheckedOutCartAgainRecordsNothing() {
        given(added(shoes, 10), new ShoppingCartCheckedOut(shoppingCartId, now))
                .when(current -> decide(checkOut(Map.of(shoes.productId(), 10.0)), current))
                .thenNothing();
    }

    @Test
    public void addingToACheckedOutCartThrows() {
        given(added(shoes, 10), new ShoppingCartCheckedOut(shoppingCartId, now))
                .when(current -> decide(add(shirt, 5), current))
                .thenThrows(IllegalStateException.class);
    }

    private AddProductItemToShoppingCart add(ProductItem productItem, double currentUnitPrice) {
        return new AddProductItemToShoppingCart(shoppingCartId, customerId, productItem, currentUnitPrice, now);
    }

    private RemoveProductItemFromShoppingCart remove(ProductItem productItem) {
        return new RemoveProductItemFromShoppingCart(shoppingCartId, productItem, 0L, now);
    }

    private CheckOutShoppingCart checkOut(Map<UUID, Double> currentUnitPrices) {
        return new CheckOutShoppingCart(shoppingCartId, 0L, currentUnitPrices, now);
    }

    private ProductItemAddedToShoppingCart added(ProductItem productItem, double unitPrice) {
        return new ProductItemAddedToShoppingCart(
                shoppingCartId, customerId, new PricedProductItem(productItem, unitPrice), now);
    }
}
