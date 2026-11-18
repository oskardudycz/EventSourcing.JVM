package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class CustomerShoppingCartsTests {
    private final UUID customerId = UUID.randomUUID();
    private final ShoppingCartId firstCartId = ShoppingCartId.of(customerId, 1);
    private final ShoppingCartId secondCartId = ShoppingCartId.of(customerId, 2);
    private final PricedProductItem shoes = new PricedProductItem(new ProductItem(UUID.randomUUID(), 1), 10);
    private final OffsetDateTime now = OffsetDateTime.now();

    private final CustomerShoppingCarts customerShoppingCarts = new CustomerShoppingCarts(new Database());

    @Test
    public void aCustomerWithNoCartHasNoCurrentCartAndZeroCarts() {
        assertThat(customerShoppingCarts.currentOf(customerId)).isEmpty();
        assertThat(customerShoppingCarts.countOf(customerId)).isZero();
    }

    @Test
    public void theFirstProductMakesItsCartTheCurrentOne() {
        handle(firstCartId, 0, new ProductItemAddedToShoppingCart(firstCartId, customerId, shoes, now));

        assertThat(customerShoppingCarts.currentOf(customerId)).contains(firstCartId);
        assertThat(customerShoppingCarts.countOf(customerId)).isEqualTo(1);
    }

    @Test
    public void aCheckedOutCartIsNoLongerCurrentButStillCounts() {
        handle(firstCartId, 0, new ProductItemAddedToShoppingCart(firstCartId, customerId, shoes, now));
        handle(firstCartId, 1, new ShoppingCartCheckedOut(firstCartId, now));

        assertThat(customerShoppingCarts.currentOf(customerId)).isEmpty();
        assertThat(customerShoppingCarts.countOf(customerId)).isEqualTo(1);
    }

    @Test
    public void theNextCartBecomesCurrent() {
        handle(firstCartId, 0, new ProductItemAddedToShoppingCart(firstCartId, customerId, shoes, now));
        handle(firstCartId, 1, new ShoppingCartCheckedOut(firstCartId, now));
        handle(secondCartId, 0, new ProductItemAddedToShoppingCart(secondCartId, customerId, shoes, now));

        assertThat(customerShoppingCarts.currentOf(customerId)).contains(secondCartId);
        assertThat(customerShoppingCarts.countOf(customerId)).isEqualTo(2);
    }

    @Test
    public void anotherCustomersCartIsNotCounted() {
        var otherCustomerId = UUID.randomUUID();
        var otherCartId = ShoppingCartId.of(otherCustomerId, 1);

        handle(otherCartId, 0, new ProductItemAddedToShoppingCart(otherCartId, otherCustomerId, shoes, now));

        assertThat(customerShoppingCarts.currentOf(customerId)).isEmpty();
        assertThat(customerShoppingCarts.countOf(customerId)).isZero();
    }

    private void handle(ShoppingCartId shoppingCartId, long position, ShoppingCartEvent event) {
        customerShoppingCarts.handle(new EventEnvelope<>(
                event, new EventMetadata(ShoppingCartFacade.mapToStreamId(shoppingCartId), position, position)));
    }
}
