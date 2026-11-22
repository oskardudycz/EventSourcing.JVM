package io.eventdriven.ecommerce.shoppingcarts.external;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.messaging.MessageEnvelope;
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
public class ShoppingCartSummariesTests {
    private static final double unitPrice = 12.5;

    private final UUID customerId = UUID.randomUUID();
    private final ShoppingCart.Id shoppingCartId = ShoppingCart.Id.of(customerId, 1);
    private final String streamId = ShoppingCartFacade.mapToStreamId(shoppingCartId);
    private final ProductItem shoes = new ProductItem(UUID.randomUUID(), 2);
    private final ProductItem shirt = new ProductItem(UUID.randomUUID(), 3);
    private final PricedProductItem pricedShoes = new PricedProductItem(shoes, unitPrice);
    private final PricedProductItem pricedShirt = new PricedProductItem(shirt, unitPrice);
    private final OffsetDateTime now = OffsetDateTime.now();

    private final ShoppingCartSummaries summaries;
    private long nextPosition = 0;

    ShoppingCartSummariesTests(ProjectionsDatabase database) {
        summaries = new ShoppingCartSummaries(database.bean(ShoppingCartSummaryRepository.class));
    }

    @Test
    public void theFirstProductStoresTheSummaryOfTheCustomer() {
        publish(added(pricedShoes));

        var summary = summary();

        assertThat(summary.getCustomerId()).isEqualTo(customerId);
        assertThat(summary.getProductItems()).usingRecursiveComparison().isEqualTo(new PricedProductItem[] {pricedShoes
        });
        assertThat(summary.getTotalPrice()).isEqualTo(25);
    }

    @Test
    public void addingProductItemsSumsTheirPrices() {
        publish(added(pricedShoes), added(pricedShirt));

        var summary = summary();

        assertThat(summary.getProductItems())
                .usingRecursiveComparison()
                .isEqualTo(new PricedProductItem[] {pricedShoes, pricedShirt});
        assertThat(summary.getTotalPrice()).isEqualTo(62.5);
    }

    @Test
    public void addingTheSameProductTwiceMergesQuantities() {
        publish(added(pricedShoes), added(pricedShoes));

        var summary = summary();

        assertThat(summary.getProductItems()).usingRecursiveComparison().isEqualTo(new PricedProductItem[] {
            new PricedProductItem(new ProductItem(shoes.productId(), 4), unitPrice)
        });
        assertThat(summary.getTotalPrice()).isEqualTo(50);
    }

    @Test
    public void removingProductItemSubtractsItsPrice() {
        publish(
                added(pricedShoes),
                added(pricedShirt),
                new ProductItemRemovedFromShoppingCart(shoppingCartId, pricedShoes, now));

        var summary = summary();

        assertThat(summary.getProductItems()).usingRecursiveComparison().isEqualTo(new PricedProductItem[] {pricedShirt
        });
        assertThat(summary.getTotalPrice()).isEqualTo(37.5);
    }

    @Test
    public void aPriceChangeUpdatesTheLineAndTheTotal() {
        publish(
                added(pricedShoes),
                added(pricedShirt),
                new ProductItemPriceChanged(shoppingCartId, shoes.productId(), unitPrice, 15, now));

        var summary = summary();

        assertThat(summary.getProductItems())
                .usingRecursiveComparison()
                .isEqualTo(new PricedProductItem[] {new PricedProductItem(shoes, 15), pricedShirt});
        assertThat(summary.getTotalPrice()).isEqualTo(67.5);
    }

    @Test
    public void checkingOutKeepsTheSummaryAndReachesItsPosition() {
        publish(added(pricedShoes), new ShoppingCartCheckedOut(shoppingCartId, now));

        var summary = summary();

        assertThat(summary.getCustomerId()).isEqualTo(customerId);
        assertThat(summary.getProductItems()).usingRecursiveComparison().isEqualTo(new PricedProductItem[] {pricedShoes
        });
        assertThat(summary.getTotalPrice()).isEqualTo(25);
        assertThat(summary.getVersion()).isEqualTo(1);
    }

    @Test
    public void redeliveredEventIsAppliedOnlyOnce() {
        publish(added(pricedShoes));

        republishAt(0, added(pricedShoes));

        var summary = summary();

        assertThat(summary.getProductItems()).usingRecursiveComparison().isEqualTo(new PricedProductItem[] {pricedShoes
        });
        assertThat(summary.getTotalPrice()).isEqualTo(25);
    }

    private ProductItemAddedToShoppingCart added(PricedProductItem productItem) {
        return new ProductItemAddedToShoppingCart(shoppingCartId, customerId, productItem, now);
    }

    private void publish(ShoppingCartEvent... events) {
        for (var event : events) {
            republishAt(nextPosition++, event);
        }
    }

    private void republishAt(long position, ShoppingCartEvent event) {
        summaries.handle(new MessageEnvelope<>(event, Metadata.at(streamId, position)));
    }

    private ShoppingCartSummary summary() {
        return summaries.get(shoppingCartId).orElseThrow();
    }
}
