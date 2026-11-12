package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ShoppingCartExternalEventForwarderTests {
  private static final double unitPrice = 12.5;

  private final ShoppingCartId shoppingCartId = ShoppingCartId.of(UUID.randomUUID());
  private final UUID clientId = UUID.randomUUID();
  private final ProductItem shoes = new ProductItem(UUID.randomUUID(), 2);
  private final ProductItem shirt = new ProductItem(UUID.randomUUID(), 3);
  private final PricedProductItem pricedShoes = new PricedProductItem(shoes, unitPrice);
  private final PricedProductItem pricedShirt = new PricedProductItem(shirt, unitPrice);
  private final ProductPriceCalculator prices = item -> new PricedProductItem(item, unitPrice);
  private final OffsetDateTime now = OffsetDateTime.now();

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
  private final MessageCatcher externalEvents = new MessageCatcher();

  public ShoppingCartExternalEventForwarderTests() {
    messageBus.use(message -> {
      if (message instanceof ShoppingCartExternalEvent)
        externalEvents.catchMessage(message);
    });
    ShoppingCartsConfig.configure(
      eventStore, messageBus, prices, () -> now
    );
  }

  @Test
  public void confirmingCartWithTwoProductItemsForwardsShoppingCartFinalized() {
    openCartWithShoesAndShirt();
    externalEvents.reset();

    messageBus.publish(List.of(new ConfirmShoppingCart(shoppingCartId, 2L)));

    externalEvents.shouldReceiveMessages(
      new ShoppingCartFinalized(
        shoppingCartId,
        clientId,
        new PricedProductItem[]{pricedShoes, pricedShirt},
        62.5,
        now
      )
    );
  }

  @Test
  public void openingAndFillingACartForwardsNothing() {
    openCartWithShoesAndShirt();

    externalEvents.shouldNotReceiveAnyEvent();
  }

  @Test
  public void removingProductItemForwardsNothing() {
    openCartWithShoesAndShirt();
    externalEvents.reset();

    messageBus.publish(List.of(new RemoveProductItemFromShoppingCart(shoppingCartId, pricedShoes, 2L)));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  @Test
  public void cancellingCartForwardsNothing() {
    openCartWithShoesAndShirt();
    externalEvents.reset();

    messageBus.publish(List.of(new CancelShoppingCart(shoppingCartId, 2L)));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  private void openCartWithShoesAndShirt() {
    messageBus.publish(List.of(
      new OpenShoppingCart(shoppingCartId, clientId),
      new AddProductItemToShoppingCart(shoppingCartId, shoes, 0L),
      new AddProductItemToShoppingCart(shoppingCartId, shirt, 1L)
    ));
  }

  @Test
  public void forwarderRefusesASummaryThatHasNotSeenTheEventSoTheStoreDeliversItAgain() {
    var summaries = new ShoppingCartSummaries(new Database());
    var bus = new InMemoryMessageBus();
    var published = new MessageCatcher();
    bus.use(published::catchMessage);
    var forwarder = new ShoppingCartExternalEventForwarder(summaries, bus);
    var streamId = ShoppingCartFacade.mapToStreamId(shoppingCartId);

    summaries.onOpened(new EventEnvelope<>(
      new ShoppingCartEvent.ShoppingCartOpened(shoppingCartId, clientId),
      new EventMetadata(streamId, 0, 0)
    ));
    summaries.onProductItemAdded(new EventEnvelope<>(
      new ShoppingCartEvent.ProductItemAddedToShoppingCart(shoppingCartId, pricedShoes),
      new EventMetadata(streamId, 1, 1)
    ));
    var confirmed = new EventEnvelope<>(
      new ShoppingCartEvent.ShoppingCartConfirmed(shoppingCartId, now),
      new EventMetadata(streamId, 2, 2)
    );

    assertThatThrownBy(() -> forwarder.onConfirmed(confirmed)).isInstanceOf(IllegalStateException.class);
    published.shouldNotReceiveAnyEvent();

    summaries.onConfirmed(confirmed);
    forwarder.onConfirmed(confirmed);

    published.shouldReceiveMessages(
      new ShoppingCartFinalized(shoppingCartId, clientId, new PricedProductItem[]{pricedShoes}, 25, now)
    );
  }
}
