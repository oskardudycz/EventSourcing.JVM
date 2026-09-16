package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartFinalized;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;

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
  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final InMemoryEventBus integrationEventBus = new InMemoryEventBus();
  private final MessageCatcher externalEvents = new MessageCatcher();

  public ShoppingCartExternalEventForwarderTests() {
    integrationEventBus.use(externalEvents::catchMessage);
    ShoppingCartsConfig.configure(
      commandBus, eventStore, eventStore, integrationEventBus, prices, () -> now
    );
  }

  @Test
  public void confirmingCartWithTwoProductItemsForwardsShoppingCartFinalized() {
    openCartWithShoesAndShirt();
    externalEvents.reset();

    commandBus.send(new ConfirmShoppingCart(shoppingCartId, 2L));

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

    commandBus.send(new RemoveProductItemFromShoppingCart(shoppingCartId, pricedShoes, 2L));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  @Test
  public void cancellingCartForwardsNothing() {
    openCartWithShoesAndShirt();
    externalEvents.reset();

    commandBus.send(new CancelShoppingCart(shoppingCartId, 2L));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  private void openCartWithShoesAndShirt() {
    commandBus.send(
      new OpenShoppingCart(shoppingCartId, clientId),
      new AddProductItemToShoppingCart(shoppingCartId, shoes, 0L),
      new AddProductItemToShoppingCart(shoppingCartId, shirt, 1L)
    );
  }
}
