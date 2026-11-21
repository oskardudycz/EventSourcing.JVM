package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.Metadata;
import io.eventdriven.testing.ProjectionsDatabase;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

@ParameterizedClass
@EnumSource(ProjectionsDatabase.class)
public class CartsTests {
    private final UUID customerId = UUID.randomUUID();
    private final ShoppingCartId cartId = ShoppingCartId.of(customerId, 1);
    private final String streamId = ShoppingCartFacade.mapToStreamId(cartId);
    private final UUID shoes = UUID.randomUUID();
    private final UUID shirt = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.now();

    private final Carts carts;

    CartsTests(ProjectionsDatabase database) {
        carts = new Carts(database.bean(CartRepository.class));
    }

    @Test
    public void theFirstProductStartsTheCartWithItsPriceAndTotal() {
        carts.handle(at(0, added(shoes, 2, 50)));

        var cart = carts.get(cartId).orElseThrow();

        assertThat(cart.getCustomerId()).isEqualTo(customerId);
        assertThat(cart.getLines()).containsExactly(new Cart.Line(shoes, 2, 50));
        assertThat(cart.getTotalPrice()).isEqualTo(100);
        assertThat(cart.isCheckedOut()).isFalse();
        assertThat(cart.getVersion()).isEqualTo(0);
    }

    @Test
    public void addingTheSameProductAgainAddsToItsLine() {
        carts.handle(at(0, added(shoes, 2, 50)));
        carts.handle(at(1, added(shirt, 1, 30)));
        carts.handle(at(2, added(shoes, 1, 50)));

        var cart = carts.get(cartId).orElseThrow();

        assertThat(cart.getLines()).containsExactly(new Cart.Line(shoes, 3, 50), new Cart.Line(shirt, 1, 30));
        assertThat(cart.getTotalPrice()).isEqualTo(180);
        assertThat(cart.getVersion()).isEqualTo(2);
    }

    @Test
    public void removingAProductLowersItsQuantityAndRemovingTheRestDropsTheLine() {
        carts.handle(at(0, added(shoes, 2, 50)));
        carts.handle(at(1, removed(shoes, 1, 50)));

        assertThat(carts.get(cartId).orElseThrow().getLines()).containsExactly(new Cart.Line(shoes, 1, 50));

        carts.handle(at(2, removed(shoes, 1, 50)));

        assertThat(carts.get(cartId).orElseThrow().getLines()).isEmpty();
        assertThat(carts.get(cartId).orElseThrow().getTotalPrice()).isEqualTo(0);
    }

    @Test
    public void aChangedPriceShowsTheNewPriceAndTotal() {
        carts.handle(at(0, added(shoes, 2, 50)));
        carts.handle(at(1, new ProductItemPriceChanged(cartId, shoes, 50, 60, now)));

        var cart = carts.get(cartId).orElseThrow();

        assertThat(cart.getLines()).containsExactly(new Cart.Line(shoes, 2, 60));
        assertThat(cart.getTotalPrice()).isEqualTo(120);
    }

    @Test
    public void checkingOutMarksTheCartCheckedOut() {
        carts.handle(at(0, added(shoes, 2, 50)));
        carts.handle(at(1, new ShoppingCartCheckedOut(cartId, now)));

        assertThat(carts.get(cartId).orElseThrow().isCheckedOut()).isTrue();
    }

    private ProductItemAddedToShoppingCart added(UUID productId, int quantity, double unitPrice) {
        return new ProductItemAddedToShoppingCart(
                cartId, customerId, new PricedProductItem(new ProductItem(productId, quantity), unitPrice), now);
    }

    private ProductItemRemovedFromShoppingCart removed(UUID productId, int quantity, double unitPrice) {
        return new ProductItemRemovedFromShoppingCart(
                cartId, new PricedProductItem(new ProductItem(productId, quantity), unitPrice), now);
    }

    private <Event> EventEnvelope<Event> at(long streamPosition, Event event) {
        return new EventEnvelope<>(event, Metadata.at(streamId, streamPosition));
    }
}
