package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

public class ShoppingCartSummariesTests {
  private static final double unitPrice = 12.5;

  private final UUID clientId = UUID.randomUUID();
  private final ShoppingCartId shoppingCartId = ShoppingCartId.of(clientId, 1);
  private final String streamId = ShoppingCartFacade.mapToStreamId(shoppingCartId);
  private final ProductItem shoes = new ProductItem(UUID.randomUUID(), 2);
  private final ProductItem shirt = new ProductItem(UUID.randomUUID(), 3);
  private final PricedProductItem pricedShoes = new PricedProductItem(shoes, unitPrice);
  private final PricedProductItem pricedShirt = new PricedProductItem(shirt, unitPrice);
  private final OffsetDateTime now = OffsetDateTime.now();

  private final ShoppingCartSummaries summaries = new ShoppingCartSummaries(new Database());
  private long nextPosition = 0;

  @Test
  public void theFirstProductStoresTheSummaryOfTheClient() {
    publish(added(pricedShoes));

    var summary = summary();

    assertThat(summary.getClientId()).isEqualTo(clientId);
    assertThat(summary.getProductItems())
      .usingRecursiveComparison()
      .isEqualTo(new PricedProductItem[]{pricedShoes});
    assertThat(summary.getTotalPrice()).isEqualTo(25);
  }

  @Test
  public void addingProductItemsSumsTheirPrices() {
    publish(
      added(pricedShoes),
      added(pricedShirt)
    );

    var summary = summary();

    assertThat(summary.getProductItems())
      .usingRecursiveComparison()
      .isEqualTo(new PricedProductItem[]{pricedShoes, pricedShirt});
    assertThat(summary.getTotalPrice()).isEqualTo(62.5);
  }

  @Test
  public void addingTheSameProductTwiceMergesQuantities() {
    publish(
      added(pricedShoes),
      added(pricedShoes)
    );

    var summary = summary();

    assertThat(summary.getProductItems())
      .usingRecursiveComparison()
      .isEqualTo(new PricedProductItem[]{new PricedProductItem(new ProductItem(shoes.productId(), 4), unitPrice)});
    assertThat(summary.getTotalPrice()).isEqualTo(50);
  }

  @Test
  public void removingProductItemSubtractsItsPrice() {
    publish(
      added(pricedShoes),
      added(pricedShirt),
      new ProductItemRemovedFromShoppingCart(shoppingCartId, pricedShoes, now)
    );

    var summary = summary();

    assertThat(summary.getProductItems())
      .usingRecursiveComparison()
      .isEqualTo(new PricedProductItem[]{pricedShirt});
    assertThat(summary.getTotalPrice()).isEqualTo(37.5);
  }

  @Test
  public void aPriceChangeUpdatesTheLineAndTheTotal() {
    publish(
      added(pricedShoes),
      added(pricedShirt),
      new ProductItemPriceChanged(shoppingCartId, shoes.productId(), unitPrice, 15, now)
    );

    var summary = summary();

    assertThat(summary.getProductItems())
      .usingRecursiveComparison()
      .isEqualTo(new PricedProductItem[]{new PricedProductItem(shoes, 15), pricedShirt});
    assertThat(summary.getTotalPrice()).isEqualTo(67.5);
  }

  @Test
  public void checkingOutKeepsTheSummaryAndReachesItsPosition() {
    publish(
      added(pricedShoes),
      new ShoppingCartCheckedOut(shoppingCartId, now)
    );

    var summary = summary();

    assertThat(summary.getClientId()).isEqualTo(clientId);
    assertThat(summary.getProductItems())
      .usingRecursiveComparison()
      .isEqualTo(new PricedProductItem[]{pricedShoes});
    assertThat(summary.getTotalPrice()).isEqualTo(25);
    assertThat(summary.getVersion()).isEqualTo(1);
  }

  @Test
  public void redeliveredEventIsAppliedOnlyOnce() {
    publish(added(pricedShoes));

    republishAt(0, added(pricedShoes));

    var summary = summary();

    assertThat(summary.getProductItems())
      .usingRecursiveComparison()
      .isEqualTo(new PricedProductItem[]{pricedShoes});
    assertThat(summary.getTotalPrice()).isEqualTo(25);
  }

  private ProductItemAddedToShoppingCart added(PricedProductItem productItem) {
    return new ProductItemAddedToShoppingCart(shoppingCartId, clientId, productItem, now);
  }

  private void publish(ShoppingCartEvent... events) {
    for (var event : events) {
      republishAt(nextPosition++, event);
    }
  }

  private void republishAt(long position, ShoppingCartEvent event) {
    summaries.handle(new EventEnvelope<>(event, new EventMetadata(streamId, position, position)));
  }

  private ShoppingCartSummary summary() {
    return summaries.get(shoppingCartId).orElseThrow();
  }
}
