package io.eventdriven.ecommerce.shoppingcarts.currentcart;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.messaging.Metadata;
import io.eventdriven.testing.projections.ProjectionsDatabase;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

@ParameterizedClass
@EnumSource(ProjectionsDatabase.class)
public class CustomerShoppingCartsTests {
    private final UUID customerId = UUID.randomUUID();
    private final ShoppingCart.Id firstCartId = ShoppingCart.Id.of(customerId, 1);
    private final ShoppingCart.Id secondCartId = ShoppingCart.Id.of(customerId, 2);
    private final PricedProductItem shoes = new PricedProductItem(new ProductItem(UUID.randomUUID(), 1), 10);
    private final OffsetDateTime now = OffsetDateTime.now();

    private final CustomerShoppingCarts customerShoppingCarts;

    CustomerShoppingCartsTests(ProjectionsDatabase database) {
        customerShoppingCarts = new CustomerShoppingCarts(database.bean(CustomerShoppingCartRepository.class));
    }

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
        var otherCartId = ShoppingCart.Id.of(otherCustomerId, 1);

        handle(otherCartId, 0, new ProductItemAddedToShoppingCart(otherCartId, otherCustomerId, shoes, now));

        assertThat(customerShoppingCarts.currentOf(customerId)).isEmpty();
        assertThat(customerShoppingCarts.countOf(customerId)).isZero();
    }

    private void handle(ShoppingCart.Id shoppingCartId, long position, ShoppingCartEvent event) {
        customerShoppingCarts.handle(
                new EventEnvelope<>(event, Metadata.at(ShoppingCartFacade.mapToStreamId(shoppingCartId), position)));
    }
}
