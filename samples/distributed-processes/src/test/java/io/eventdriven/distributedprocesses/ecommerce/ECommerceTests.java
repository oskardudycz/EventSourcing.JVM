package io.eventdriven.distributedprocesses.ecommerce;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.external.OrderExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.payments.AutoRejectingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.DeclineReason;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.RecordingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
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

import static io.eventdriven.distributedprocesses.ecommerce.shipments.delivery.CallingBackDeliveryProvider.carrier;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.delivery.CallingBackDeliveryProvider.trackingNumber;
import static org.assertj.core.api.Assertions.assertThat;

public class ECommerceTests {
  private static final double unitPrice = 100;
  private static final Duration authorizationValidity = Duration.ofDays(7);
  private static final Duration reservationValidity = Duration.ofHours(2);

  private final OffsetDateTime now = OffsetDateTime.now();
  private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
  private final UUID clientId = UUID.randomUUID();
  private final ProductItem shoes = new ProductItem(UUID.randomUUID(), 2);
  private final double totalPrice = shoes.quantity() * unitPrice;

  private final OrderId orderId = OrderId.derivedFrom(cartId.value());
  private final String orderReference = orderId.value();
  private final PaymentId paymentId = PaymentId.derivedFrom(orderReference);
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(orderReference);

  private final io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem[] orderItems = {
    new io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem(
      shoes.productId(), shoes.quantity(), unitPrice
    )
  };
  private final io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem[] shipmentItems = {
    new io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem(
      shoes.productId(), shoes.quantity()
    )
  };

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final InMemoryEventBus integrationEventBus = new InMemoryEventBus();
  private final MessageCatcher messages = new MessageCatcher();
  private final CallingBackDeliveryProvider deliveryProvider = new CallingBackDeliveryProvider(commandBus);

  @Test
  public void confirmingTheCartHoldsTheMoneyAndTheStockAndAsksForTheParcel() {
    // Given
    configure(new RecordingPaymentGateway(commandBus), true);
    openCartWithShoes();

    // When
    confirmCart();

    // Then
    messages.shouldReceiveMessages(
      new ConfirmShoppingCart(cartId, 1L),
      cartFinalized(),
      initializeOrder(),
      new AuthorizePayment(orderReference, totalPrice),
      new ConfirmPaymentAuthorization(paymentId),
      new PaymentExternalEvent.PaymentAuthorized(
        orderReference, paymentId, totalPrice, now, now.plus(authorizationValidity)
      ),
      new RecordOrderPaymentAuthorization(orderId, paymentId, now),
      new ReserveStock(orderReference, shipmentItems),
      new ShipmentExternalEvent.StockReserved(
        shipmentId, orderReference, shipmentItems, now, now.plus(reservationValidity)
      ),
      new RecordOrderStockReservation(orderId, shipmentId, now),
      new OrderExternalEvent.OrderConfirmed(orderId, clientId, orderItems, totalPrice, now),
      new SendPackage(shipmentId)
    );
  }

  @Test
  public void theCustomerIsChargedOnlyAfterTheParcelLeftAndGetsItsTracking() {
    // Given
    configure(new RecordingPaymentGateway(commandBus), true);
    openCartWithShoes();
    confirmCart();
    messages.reset();

    // When
    deliveryProvider.confirmBookings();

    // Then
    messages.shouldReceiveMessages(
      new ConfirmParcelBooking(shipmentId, carrier, trackingNumber),
      new ShipmentExternalEvent.PackageWasSent(
        shipmentId, orderReference, shipmentItems, carrier, trackingNumber, now
      ),
      new RecordOrderPackageSent(orderId, carrier, trackingNumber, now),
      new OrderExternalEvent.OrderShipped(orderId, clientId, shipmentId, carrier, trackingNumber, now),
      new CapturePayment(paymentId),
      new PaymentExternalEvent.PaymentCaptured(orderReference, paymentId, totalPrice, now),
      new RecordOrderPaymentCapture(orderId, now)
    );
  }

  @Test
  public void deliveringTheParcelCompletesTheOrder() {
    // Given
    configure(new RecordingPaymentGateway(commandBus), true);
    openCartWithShoes();
    confirmCart();
    deliveryProvider.confirmBookings();
    messages.reset();

    // When
    deliveryProvider.deliverParcels();

    // Then
    messages.shouldReceiveMessages(
      new DeliverPackage(shipmentId),
      new ShipmentExternalEvent.PackageWasDelivered(shipmentId, orderReference, now),
      new RecordOrderDelivery(orderId, now),
      new OrderExternalEvent.OrderCompleted(orderId, clientId, orderItems, totalPrice, now)
    );
  }

  @Test
  public void productsOutOfStockCancelTheOrderAndReleaseTheHoldWithoutCharging() {
    // Given
    var paymentGateway = new RecordingPaymentGateway(commandBus);
    configure(paymentGateway, false);
    openCartWithShoes();

    // When
    confirmCart();

    // Then
    messages.shouldReceiveMessages(
      new ConfirmShoppingCart(cartId, 1L),
      cartFinalized(),
      initializeOrder(),
      new AuthorizePayment(orderReference, totalPrice),
      new ConfirmPaymentAuthorization(paymentId),
      new PaymentExternalEvent.PaymentAuthorized(
        orderReference, paymentId, totalPrice, now, now.plus(authorizationValidity)
      ),
      new RecordOrderPaymentAuthorization(orderId, paymentId, now),
      new ReserveStock(orderReference, shipmentItems),
      new ShipmentExternalEvent.StockReservationFailed(
        shipmentId,
        orderReference,
        shipmentItems,
        ShipmentExternalEvent.StockReservationFailed.Reason.ProductsOutOfStock,
        now
      ),
      new RecordOrderShipmentFailure(orderId, now),
      new OrderExternalEvent.OrderCancelled(
        orderId,
        clientId,
        orderItems,
        totalPrice,
        OrderExternalEvent.OrderCancelled.Reason.ProductsOutOfStock,
        now
      ),
      new VoidPayment(paymentId)
    );
    assertThat(paymentGateway.calls).contains("void:%s".formatted(paymentId));
  }

  @Test
  public void aDeclinedPaymentCancelsTheOrderAndReleasesTheStock() {
    // Given
    configure(new AutoRejectingPaymentGateway(commandBus), true);
    openCartWithShoes();

    // When
    confirmCart();

    // Then
    messages.shouldReceiveMessages(
      new ConfirmShoppingCart(cartId, 1L),
      cartFinalized(),
      initializeOrder(),
      new AuthorizePayment(orderReference, totalPrice),
      new DeclinePayment(paymentId, DeclineReason.UnexpectedError),
      new PaymentExternalEvent.PaymentFailed(
        orderReference, paymentId, totalPrice, now, PaymentExternalEvent.PaymentFailed.Reason.Declined
      ),
      new RecordOrderPaymentFailure(orderId, now),
      new ReserveStock(orderReference, shipmentItems),
      new ShipmentExternalEvent.StockReserved(
        shipmentId, orderReference, shipmentItems, now, now.plus(reservationValidity)
      ),
      new RecordOrderStockReservation(orderId, shipmentId, now),
      new OrderExternalEvent.OrderCancelled(
        orderId,
        clientId,
        orderItems,
        totalPrice,
        OrderExternalEvent.OrderCancelled.Reason.PaymentFailed,
        now
      ),
      new ReleaseStock(shipmentId)
    );
  }

  private ShoppingCartExternalEvent.ShoppingCartFinalized cartFinalized() {
    return new ShoppingCartExternalEvent.ShoppingCartFinalized(
      cartId, clientId, new PricedProductItem[]{new PricedProductItem(shoes, unitPrice)}, totalPrice, now
    );
  }

  private InitializeOrder initializeOrder() {
    return new InitializeOrder(orderId, cartId, clientId, orderItems, totalPrice);
  }

  private void openCartWithShoes() {
    commandBus.send(new OpenShoppingCart(cartId, clientId));
    commandBus.send(new AddProductItemToShoppingCart(cartId, shoes, 0L));
    messages.reset();
  }

  private void confirmCart() {
    commandBus.send(new ConfirmShoppingCart(cartId, 1L));
  }

  private void configure(PaymentGateway paymentGateway, boolean inStock) {
    commandBus.use(messages::catchMessage);
    integrationEventBus.use(messages::catchMessage);

    ECommerceConfig.configure(
      commandBus,
      eventStore,
      eventStore,
      integrationEventBus,
      item -> new PricedProductItem(item, unitPrice),
      paymentGateway,
      Duration.ofMinutes(15),
      authorizationValidity,
      deliveryProvider,
      item -> inStock,
      reservationValidity,
      () -> now
    );
  }
}
