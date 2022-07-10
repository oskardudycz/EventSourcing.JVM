package io.eventdriven.introductiontoeventsourcing.solved.e05_business_logic.mutable.solution1;

import java.time.OffsetDateTime;
import java.util.*;

import static io.eventdriven.introductiontoeventsourcing.solved.e05_business_logic.mutable.solution1.BusinessLogicTests.*;
import static io.eventdriven.introductiontoeventsourcing.solved.e05_business_logic.mutable.solution1.BusinessLogicTests.ShoppingCartEvent.*;

public class BusinessLogic {
  public static abstract class Aggregate<Event> {
    protected UUID id;

    private final Queue<Object> uncommittedEvents = new LinkedList<>();

    public UUID id() {
      return id;
    }

    public Object[] dequeueUncommittedEvents() {
      var dequeuedEvents = uncommittedEvents.toArray();

      uncommittedEvents.clear();

      return dequeuedEvents;
    }

    public abstract void when(Event event);

    protected void enqueue(Event event) {
      uncommittedEvents.add(event);
      when(event);
    }
  }

  public static class ShoppingCart extends Aggregate<ShoppingCartEvent> {
    private UUID clientId;
    private ShoppingCartStatus status;
    private List<PricedProductItem> productItems;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime canceledAt;

    public ShoppingCart() {
    }

    private ShoppingCart(
      UUID id,
      UUID clientId
    ) {
      enqueue(new ShoppingCartOpened(id, clientId));
    }

    public static ShoppingCart open(UUID shoppingCartId, UUID clientId) {
      return new ShoppingCart(
        shoppingCartId,
        clientId
      );
    }

    void addProduct(
      ProductPriceCalculator productPriceCalculator,
      ProductItem productItem
    ) {
      if (isClosed())
        throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(status));

      var pricedProductItem = productPriceCalculator.calculate(productItem);

      enqueue(new ProductItemAddedToShoppingCart(
        id,
        pricedProductItem
      ));
    }

    void removeProduct(
      PricedProductItem productItem
    ) {
      if (isClosed())
        throw new IllegalStateException("Adding product item for cart in '%s' status is not allowed.".formatted(status));

      if (!hasEnough(productItem))
        throw new IllegalStateException("Not enough product items to remove");

      enqueue(new ProductItemRemovedFromShoppingCart(
        id,
        productItem
      ));
    }

    void confirm() {
      if (isClosed())
        throw new IllegalStateException("Confirming cart in '%s' status is not allowed.".formatted(status));

      enqueue(new ShoppingCartConfirmed(
        id,
        OffsetDateTime.now()
      ));
    }

    void cancel() {
      if (isClosed())
        throw new IllegalStateException("Canceling cart in '%s' status is not allowed.".formatted(status));

      enqueue(new ShoppingCartCanceled(
        id,
        OffsetDateTime.now()
      ));
    }

    private boolean isClosed() {
      return status == ShoppingCartStatus.Confirmed || status == ShoppingCartStatus.Canceled;
    }

    public boolean hasEnough(PricedProductItem productItem) {
      var currentQuantity = productItems.stream()
        .filter(pi -> pi.productId().equals(productItem.productId()))
        .mapToInt(PricedProductItem::quantity)
        .sum();

      return currentQuantity >= productItem.quantity();
    }

    public UUID clientId() {
      return clientId;
    }

    public ShoppingCartStatus status() {
      return status;
    }

    public PricedProductItem[] productItems() {
      return productItems.toArray(PricedProductItem[]::new);
    }

    public OffsetDateTime confirmedAt() {
      return confirmedAt;
    }

    public OffsetDateTime canceledAt() {
      return canceledAt;
    }

    @Override
    public void when(ShoppingCartEvent event) {
      switch (event) {
        case ShoppingCartOpened opened -> apply(opened);
        case ProductItemAddedToShoppingCart productItemAdded ->
          apply(productItemAdded);
        case ProductItemRemovedFromShoppingCart productItemRemoved ->
          apply(productItemRemoved);
        case ShoppingCartConfirmed confirmed -> apply(confirmed);
        case ShoppingCartCanceled canceled -> apply(canceled);
      }
    }

    private void apply(ShoppingCartOpened event) {
      id = event.shoppingCartId();
      clientId = event.clientId();
      status = ShoppingCartStatus.Pending;
      productItems = new ArrayList<>();
    }

    private void apply(ProductItemAddedToShoppingCart event) {
      var pricedProductItem = event.productItem();
      var productId = pricedProductItem.productId();
      var quantityToAdd = pricedProductItem.quantity();

      productItems.stream()
        .filter(pi -> pi.productId().equals(productId))
        .findAny()
        .ifPresentOrElse(
          current -> current.add(quantityToAdd),
          () -> productItems.add(pricedProductItem)
        );
    }

    private void apply(ProductItemRemovedFromShoppingCart event) {
      var pricedProductItem = event.productItem();
      var productId = pricedProductItem.productId();
      var quantityToRemove = pricedProductItem.quantity();

      productItems.stream()
        .filter(pi -> pi.productId().equals(productId))
        .findAny()
        .ifPresentOrElse(
          current -> current.subtract(quantityToRemove),
          () -> productItems.add(pricedProductItem)
        );
    }

    private void apply(ShoppingCartConfirmed event) {
      status = ShoppingCartStatus.Confirmed;
      confirmedAt = event.confirmedAt();
    }

    private void apply(ShoppingCartCanceled event) {
      status = ShoppingCartStatus.Canceled;
      confirmedAt = event.canceledAt();
    }
  }

  public enum ShoppingCartStatus {
    Pending,
    Confirmed,
    Canceled
  }

  public interface ProductPriceCalculator {
    PricedProductItem calculate(ProductItem productItems);
  }

  public static class FakeProductPriceCalculator implements ProductPriceCalculator {
    private final double value;

    private FakeProductPriceCalculator(double value) {
      this.value = value;
    }

    public static FakeProductPriceCalculator returning(double value) {
      return new FakeProductPriceCalculator(value);
    }

    public PricedProductItem calculate(ProductItem productItem) {
      return new PricedProductItem(productItem.productId(), productItem.quantity(), value);
    }
  }
}
