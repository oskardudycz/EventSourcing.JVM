package io.eventdriven.distributedprocesses.ecommerce;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;
import io.eventdriven.distributedprocesses.ecommerce.orders.external.OrderExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.payments.AutoRejectingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.RecordingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;
import io.eventdriven.distributedprocesses.ecommerce.shipments.delivery.CallingBackDeliveryProvider;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// The whole order process on one command bus and one integration bus: the cart, the order,
// the payment and the shipment only meet through commands and public events.
public class ECommerceTests {
  private final OffsetDateTime now = OffsetDateTime.now();
  private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
  private final ProductItem shoes = new ProductItem(UUID.randomUUID(), 2);

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final InMemoryEventBus integrationEventBus = new InMemoryEventBus();
  private final MessageCatcher publicEvents = new MessageCatcher();
  private final MessageCatcher commands = new MessageCatcher();
  private final CallingBackDeliveryProvider deliveryProvider = new CallingBackDeliveryProvider(commandBus);

  @Test
  public void theCustomerIsChargedOnlyAfterTheParcelLeft() {
    var paymentGateway = new RecordingPaymentGateway(commandBus);
    configure(paymentGateway, true);

    confirmCart();
    deliveryProvider.deliverBookedParcels();

    assertThat(commands.published).extracting(Object::getClass).containsExactly(
      ConfirmShoppingCart.class,
      InitializeOrder.class,
      AuthorizePayment.class,
      ConfirmPaymentAuthorization.class,
      RecordOrderPaymentAuthorization.class,
      ReserveStock.class,
      RecordOrderStockReservation.class,
      SendPackage.class,
      RecordOrderPackageSent.class,
      CapturePayment.class,
      RecordOrderPaymentCapture.class,
      DeliverPackage.class,
      RecordOrderDelivery.class
    );
    assertThat(publicEvents.published).extracting(Object::getClass).containsExactly(
      ShoppingCartExternalEvent.ShoppingCartFinalized.class,
      PaymentExternalEvent.PaymentAuthorized.class,
      ShipmentExternalEvent.StockReserved.class,
      OrderExternalEvent.OrderConfirmed.class,
      ShipmentExternalEvent.PackageWasSent.class,
      OrderExternalEvent.OrderShipped.class,
      PaymentExternalEvent.PaymentCaptured.class,
      ShipmentExternalEvent.PackageWasDelivered.class,
      OrderExternalEvent.OrderCompleted.class
    );
    assertThat(paymentGateway.calls)
      .extracting(call -> call.split(":")[0])
      .containsExactly("authorize", "capture");
  }

  @Test
  public void theCustomerTellsWhereTheParcelIs() {
    configure(new RecordingPaymentGateway(commandBus), true);

    confirmCart();

    assertThat(publicEvents.published)
      .filteredOn(OrderExternalEvent.OrderShipped.class::isInstance)
      .singleElement()
      .usingRecursiveComparison()
      .comparingOnlyFields("carrier", "trackingNumber")
      .isEqualTo(CallingBackDeliveryProvider.booking);
  }

  @Test
  public void productsOutOfStockCancelTheOrderAndReleaseTheHoldWithoutCharging() {
    var paymentGateway = new RecordingPaymentGateway(commandBus);
    configure(paymentGateway, false);

    confirmCart();

    assertThat(publicEvents.published).extracting(Object::getClass).containsExactly(
      ShoppingCartExternalEvent.ShoppingCartFinalized.class,
      PaymentExternalEvent.PaymentAuthorized.class,
      ShipmentExternalEvent.StockReservationFailed.class,
      OrderExternalEvent.OrderCancelled.class
    );
    assertThat(publicEvents.published.getLast())
      .usingRecursiveComparison()
      .comparingOnlyFields("reason")
      .isEqualTo(new OrderExternalEvent.OrderCancelled(
        null, null, null, 0, OrderExternalEvent.OrderCancelled.Reason.ProductsOutOfStock, null
      ));
    assertThat(commands.published).extracting(Object::getClass)
      .contains(VoidPayment.class)
      .doesNotContain(CapturePayment.class, ReleaseStock.class, SendPackage.class);
    assertThat(paymentGateway.calls)
      .extracting(call -> call.split(":")[0])
      .containsExactly("authorize", "void");
  }

  @Test
  public void aDeclinedPaymentCancelsTheOrderAndReleasesTheStock() {
    configure(new AutoRejectingPaymentGateway(commandBus), true);

    confirmCart();

    assertThat(publicEvents.published).extracting(Object::getClass).containsExactly(
      ShoppingCartExternalEvent.ShoppingCartFinalized.class,
      PaymentExternalEvent.PaymentFailed.class,
      ShipmentExternalEvent.StockReserved.class,
      OrderExternalEvent.OrderCancelled.class
    );
    assertThat(commands.published).extracting(Object::getClass)
      .contains(ReleaseStock.class)
      .doesNotContain(VoidPayment.class, SendPackage.class, CapturePayment.class);
  }

  private void confirmCart() {
    commandBus.send(new OpenShoppingCart(cartId, UUID.randomUUID()));
    commandBus.send(new AddProductItemToShoppingCart(cartId, shoes, 0L));
    commands.reset();

    commandBus.send(new ConfirmShoppingCart(cartId, 1L));
  }

  private void configure(PaymentGateway paymentGateway, boolean inStock) {
    commandBus.use(commands::catchMessage);
    integrationEventBus.use(publicEvents::catchMessage);

    ECommerceConfig.configure(
      commandBus,
      eventStore,
      eventStore,
      integrationEventBus,
      item -> new PricedProductItem(item, 100),
      paymentGateway,
      Duration.ofMinutes(15),
      Duration.ofDays(7),
      deliveryProvider,
      item -> inStock,
      Duration.ofHours(2),
      () -> now
    );
  }
}
