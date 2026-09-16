package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ShoppingCartFacadeTests {
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
  private final MessageCatcher published = new MessageCatcher();

  private final AggregateStore<ShoppingCart, ShoppingCartEvent, ShoppingCartId> store =
    new AggregateStore<>(eventStore, ShoppingCartFacade::mapToStreamId, ShoppingCart::empty);

  public ShoppingCartFacadeTests() {
    eventStore.use(published::catchMessage);
    ShoppingCartsConfig.configure(
      commandBus, eventStore, eventStore, new InMemoryEventBus(), prices, () -> now
    );
  }

  @Test
  public void openShoppingCartDispatchesAndStoresShoppingCartOpened() {
    commandBus.send(new OpenShoppingCart(shoppingCartId, clientId));

    var opened = new ShoppingCartOpened(shoppingCartId, clientId);

    published.shouldReceiveMessages(opened);
    assertThat(storedEvents()).usingRecursiveComparison().isEqualTo(List.of(opened));
    assertThat(currentCart().clientId()).isEqualTo(clientId);
  }

  @Test
  public void addProductItemDispatchesProductItemAddedAndPricesIt() {
    commandBus.send(new OpenShoppingCart(shoppingCartId, clientId));
    published.reset();

    commandBus.send(new AddProductItemToShoppingCart(shoppingCartId, shoes, 0L));

    published.shouldReceiveMessages(new ProductItemAddedToShoppingCart(shoppingCartId, pricedShoes));
    assertThat(currentCart().productItems()).containsExactly(pricedShoes);
    assertThat(currentCart().totalPrice()).isEqualTo(25);
  }

  @Test
  public void removeProductItemDispatchesProductItemRemoved() {
    openCartWithShoesAndShirt();
    published.reset();

    commandBus.send(new RemoveProductItemFromShoppingCart(shoppingCartId, pricedShoes, 2L));

    published.shouldReceiveMessages(
      new ProductItemRemovedFromShoppingCart(shoppingCartId, pricedShoes)
    );
    assertThat(currentCart().productItems()).containsExactly(pricedShirt);
  }

  @Test
  public void confirmCartWithTwoProductItemsDispatchesShoppingCartConfirmed() {
    openCartWithShoesAndShirt();
    published.reset();

    commandBus.send(new ConfirmShoppingCart(shoppingCartId, 2L));

    published.shouldReceiveMessages(new ShoppingCartConfirmed(shoppingCartId, now));
    assertThat(currentCart().productItems()).containsExactly(pricedShoes, pricedShirt);
    assertThat(currentCart().totalPrice()).isEqualTo(62.5);
  }

  @Test
  public void cancelCartDispatchesShoppingCartCanceled() {
    commandBus.send(new OpenShoppingCart(shoppingCartId, clientId));
    published.reset();

    commandBus.send(new CancelShoppingCart(shoppingCartId, 0L));

    published.shouldReceiveMessages(new ShoppingCartCanceled(shoppingCartId, now));
  }

  @Test
  public void commandWithStaleExpectedVersionFailsInsteadOfAppending() {
    openCartWithShoesAndShirt();
    published.reset();

    assertThatThrownBy(() -> commandBus.send(new ConfirmShoppingCart(shoppingCartId, 0L)))
      .isInstanceOf(IllegalStateException.class);

    published.shouldNotReceiveAnyEvent();
    assertThat(storedEvents()).hasSize(3);
  }

  private void openCartWithShoesAndShirt() {
    commandBus.send(
      new OpenShoppingCart(shoppingCartId, clientId),
      new AddProductItemToShoppingCart(shoppingCartId, shoes, 0L),
      new AddProductItemToShoppingCart(shoppingCartId, shirt, 1L)
    );
  }

  private ShoppingCart currentCart() {
    return store.get(shoppingCartId).orElseThrow();
  }

  private List<Object> storedEvents() {
    return switch (eventStore.read(ShoppingCartFacade.mapToStreamId(shoppingCartId))) {
      case EventStore.ReadResult.Success success -> List.of(success.events());
      default -> List.of();
    };
  }
}
