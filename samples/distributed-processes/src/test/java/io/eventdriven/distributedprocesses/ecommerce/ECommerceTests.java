package io.eventdriven.distributedprocesses.ecommerce;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCancellationReason;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.pending.PendingOrder;
import io.eventdriven.distributedprocesses.ecommerce.payments.AutoRejectingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.CaptureFailureReason;
import io.eventdriven.distributedprocesses.ecommerce.payments.DeclineReason;
import io.eventdriven.distributedprocesses.ecommerce.payments.FailingFirstCapturePaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.RecordingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.RefusingAfterAuthorizationPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.SilentPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.CallingBackFulfillmentService;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.CallingBackFulfillmentService.carrier;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.CallingBackFulfillmentService.trackingNumber;
import static org.assertj.core.api.Assertions.assertThat;

public class ECommerceTests {
  private static final double unitPrice = 100;
  private static final Duration paymentTimeout = Duration.ofMinutes(15);
  private static final Duration authorizationValidity = Duration.ofDays(7);
  private static final Duration paymentRetryPeriod = Duration.ofDays(14);

  private final OffsetDateTime now = OffsetDateTime.now();
  private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
  private final UUID clientId = UUID.randomUUID();
  private final ProductItem shoes = new ProductItem(UUID.randomUUID(), 2);
  private final double totalPrice = shoes.quantity() * unitPrice;

  private final OrderId orderId = OrderId.derivedFrom(cartId.value());
  private final String orderReference = orderId.value();
  private final PaymentId paymentId = PaymentId.derivedFrom(orderReference);
  private final PaymentId retriedPaymentId = PaymentId.derivedFrom(orderReference, 2);
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
  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
  private final MessageCatcher messages = new MessageCatcher();
  private final AtomicReference<OffsetDateTime> clock = new AtomicReference<>(now);
  private final CallingBackFulfillmentService fulfillmentService = new CallingBackFulfillmentService(messageBus);
  private ECommerceModule eCommerce;

  @Test
  public void confirmingTheCartHoldsTheMoneyAndTheStockAndRequestsFulfillment() {
    // Given
    configure(new RecordingPaymentGateway(messageBus), true);
    openCartWithShoes();

    // When
    confirmCart();

    // Then
    messages.shouldReceiveMessages(
      new ConfirmShoppingCart(cartId, 1L),
      cartFinalized(),
      placeOrder(),
      new AuthorizePayment(paymentId, orderReference, totalPrice),
      new ReserveStock(orderReference, shipmentItems),
      new ConfirmPaymentAuthorization(paymentId),
      new ShipmentExternalEvent.StockReserved(shipmentId, orderReference, now),
      new RecordOrderStockReservation(orderId, shipmentId, now),
      new PaymentExternalEvent.PaymentAuthorized(orderReference, paymentId, now),
      new RecordOrderPaymentAuthorization(orderId, paymentId, now),
      new RequestFulfillment(shipmentId)
    );
  }

  @Test
  public void theCustomerIsChargedOnlyAfterThePackageWasSentAndThatCompletesTheOrder() {
    // Given
    configure(new RecordingPaymentGateway(messageBus), true);
    openCartWithShoes();
    confirmCart();
    messages.reset();

    // When
    fulfillmentService.sendPackages();

    // Then
    messages.shouldReceiveMessages(
      new ConfirmPackageSent(shipmentId, carrier, trackingNumber),
      new ShipmentExternalEvent.PackageWasSent(shipmentId, orderReference, carrier, trackingNumber, now),
      new RecordOrderPackageSent(orderId, carrier, trackingNumber, now),
      new CapturePayment(paymentId),
      new ConfirmPaymentCapture(paymentId),
      new PaymentExternalEvent.PaymentCaptured(orderReference, paymentId, now),
      new RecordOrderPaymentCapture(orderId, now)
    );
    assertThat(eCommerce.orders().pendingOrders().all()).isEmpty();
  }

  @Test
  public void theDeliveryIsRecordedByTheShipmentOnlyBecauseTheOrderAlreadyCompleted() {
    // Given
    configure(new RecordingPaymentGateway(messageBus), true);
    openCartWithShoes();
    confirmCart();
    fulfillmentService.sendPackages();
    messages.reset();

    // When
    fulfillmentService.deliverPackages();

    // Then
    messages.shouldReceiveMessages(new DeliverPackage(shipmentId));
  }

  @Test
  public void productsOutOfStockCancelTheOrderAndReleaseTheHoldWithoutCharging() {
    // Given
    var paymentGateway = new RecordingPaymentGateway(messageBus);
    configure(paymentGateway, false);
    openCartWithShoes();

    // When
    confirmCart();

    // Then
    messages.shouldReceiveMessages(
      new ConfirmShoppingCart(cartId, 1L),
      cartFinalized(),
      placeOrder(),
      new AuthorizePayment(paymentId, orderReference, totalPrice),
      new ReserveStock(orderReference, shipmentItems),
      new ConfirmPaymentAuthorization(paymentId),
      new ShipmentExternalEvent.ShipmentFailed(
        shipmentId, orderReference, ShipmentExternalEvent.ShipmentFailed.Reason.ProductsOutOfStock, now
      ),
      new RecordOrderShipmentFailure(orderId, OrderCancellationReason.ProductWasOutOfStock, now),
      new PaymentExternalEvent.PaymentAuthorized(orderReference, paymentId, now),
      new RecordOrderPaymentAuthorization(orderId, paymentId, now),
      new VoidPayment(paymentId)
    );
    assertThat(paymentGateway.calls).contains("void:%s".formatted(paymentId));
  }

  @Test
  public void aDeclinedPaymentCancelsTheOrderAndReleasesTheStock() {
    // Given
    configure(new AutoRejectingPaymentGateway(messageBus), true);
    openCartWithShoes();

    // When
    confirmCart();

    // Then
    messages.shouldReceiveMessages(
      new ConfirmShoppingCart(cartId, 1L),
      cartFinalized(),
      placeOrder(),
      new AuthorizePayment(paymentId, orderReference, totalPrice),
      new ReserveStock(orderReference, shipmentItems),
      new DeclinePayment(paymentId, DeclineReason.UnexpectedError),
      new ShipmentExternalEvent.StockReserved(shipmentId, orderReference, now),
      new RecordOrderStockReservation(orderId, shipmentId, now),
      new PaymentExternalEvent.PaymentFailed(orderReference, paymentId, now),
      new RecordOrderPaymentFailure(orderId, now),
      new ReleaseStock(shipmentId)
    );
  }

  @Test
  public void aRejectedFulfillmentCancelsTheOrderVoidsTheHoldAndReleasesTheStock() {
    // Given
    var paymentGateway = new RecordingPaymentGateway(messageBus);
    configure(paymentGateway, true);
    openCartWithShoes();
    confirmCart();
    messages.reset();

    // When
    fulfillmentService.rejectRequests();

    // Then
    messages.shouldReceiveMessages(
      new RejectFulfillment(shipmentId),
      new ShipmentExternalEvent.ShipmentFailed(
        shipmentId, orderReference, ShipmentExternalEvent.ShipmentFailed.Reason.FulfillmentRejected, now
      ),
      new RecordOrderShipmentFailure(orderId, OrderCancellationReason.CouldNotShip, now),
      new VoidPayment(paymentId),
      new ReleaseStock(shipmentId)
    );
    assertThat(paymentGateway.calls).contains("void:%s".formatted(paymentId));
  }

  @Test
  public void aPaymentThatNeverAnswersTimesOutCancelsTheOrderAndReleasesTheStock() {
    // Given
    var paymentGateway = new SilentPaymentGateway();
    configure(paymentGateway, true);
    openCartWithShoes();
    confirmCart();
    messages.reset();
    var timedOutAt = now.plus(paymentTimeout).plusMinutes(1);

    // When
    clock.set(timedOutAt);
    eCommerce.payments().timeoutWorker().handleAll();

    // Then
    messages.shouldReceiveMessages(
      new TimeOutPayment(paymentId, timedOutAt),
      new PaymentExternalEvent.PaymentFailed(orderReference, paymentId, timedOutAt),
      new RecordOrderPaymentFailure(orderId, timedOutAt),
      new ReleaseStock(shipmentId)
    );
    assertThat(paymentGateway.voided).containsExactly(paymentId);
  }

  @Test
  public void anAuthorizationArrivingAfterTheTimeoutChangesNothing() {
    // Given
    configure(new SilentPaymentGateway(), true);
    openCartWithShoes();
    confirmCart();
    clock.set(now.plus(paymentTimeout).plusMinutes(1));
    eCommerce.payments().timeoutWorker().handleAll();
    messages.reset();

    // When
    messageBus.publish(List.of(new ConfirmPaymentAuthorization(paymentId)));

    // Then
    messages.shouldReceiveMessages(new ConfirmPaymentAuthorization(paymentId));
  }

  @Test
  public void aFailedCaptureAfterThePackageWasSentWaitsForTheClientToPayAgain() {
    // Given
    configure(new RefusingAfterAuthorizationPaymentGateway(messageBus), true);
    openCartWithShoes();
    confirmCart();
    messages.reset();

    // When
    fulfillmentService.sendPackages();

    // Then
    messages.shouldReceiveMessages(
      new ConfirmPackageSent(shipmentId, carrier, trackingNumber),
      new ShipmentExternalEvent.PackageWasSent(shipmentId, orderReference, carrier, trackingNumber, now),
      new RecordOrderPackageSent(orderId, carrier, trackingNumber, now),
      new CapturePayment(paymentId),
      new FailPaymentCapture(paymentId, CaptureFailureReason.UnexpectedError),
      new PaymentExternalEvent.PaymentFailed(orderReference, paymentId, now),
      new RecordOrderPaymentFailure(orderId, now)
    );
    assertThat(eCommerce.orders().pendingOrders().all())
      .extracting(PendingOrder::getOrderId)
      .containsExactly(orderId);
  }

  @Test
  public void theClientPayingAgainAfterAFailedCaptureCompletesTheOrder() {
    // Given
    configure(new FailingFirstCapturePaymentGateway(messageBus), true);
    openCartWithShoes();
    confirmCart();
    fulfillmentService.sendPackages();
    messages.reset();

    // When
    messageBus.publish(List.of(new RetryOrderPayment(orderId)));

    // Then
    messages.shouldReceiveMessages(
      new RetryOrderPayment(orderId),
      new AuthorizePayment(retriedPaymentId, orderReference, totalPrice),
      new ConfirmPaymentAuthorization(retriedPaymentId),
      new PaymentExternalEvent.PaymentAuthorized(orderReference, retriedPaymentId, now),
      new RecordOrderPaymentAuthorization(orderId, retriedPaymentId, now),
      new CapturePayment(retriedPaymentId),
      new ConfirmPaymentCapture(retriedPaymentId),
      new PaymentExternalEvent.PaymentCaptured(orderReference, retriedPaymentId, now),
      new RecordOrderPaymentCapture(orderId, now)
    );
    assertThat(eCommerce.orders().pendingOrders().all()).isEmpty();
  }

  @Test
  public void anOrderStillUnpaidAfterTheRetryPeriodIsClosedUnpaid() {
    // Given
    configure(new RefusingAfterAuthorizationPaymentGateway(messageBus), true);
    openCartWithShoes();
    confirmCart();
    fulfillmentService.sendPackages();
    messages.reset();

    // When
    clock.set(now.plus(paymentRetryPeriod));
    eCommerce.orders().unpaidOrdersWorker().handleAll();

    // Then
    messages.shouldReceiveMessages(new CloseUnpaidOrder(orderId));
    assertThat(eCommerce.orders().pendingOrders().all()).isEmpty();
  }

  @Test
  public void anOrderIsNotClosedBeforeTheRetryPeriodEnds() {
    // Given
    configure(new RefusingAfterAuthorizationPaymentGateway(messageBus), true);
    openCartWithShoes();
    confirmCart();
    fulfillmentService.sendPackages();
    messages.reset();

    // When
    clock.set(now.plus(paymentRetryPeriod).minusMinutes(1));
    eCommerce.orders().unpaidOrdersWorker().handleAll();

    // Then
    messages.shouldNotReceiveAnyEvent();
  }

  private ShoppingCartExternalEvent.ShoppingCartFinalized cartFinalized() {
    return new ShoppingCartExternalEvent.ShoppingCartFinalized(
      cartId, clientId, new PricedProductItem[]{new PricedProductItem(shoes, unitPrice)}, totalPrice, now
    );
  }

  private PlaceOrder placeOrder() {
    return new PlaceOrder(orderId, cartId, clientId, orderItems, totalPrice);
  }

  private void openCartWithShoes() {
    messageBus.publish(List.of(new OpenShoppingCart(cartId, clientId)));
    messageBus.publish(List.of(new AddProductItemToShoppingCart(cartId, shoes, 0L)));
    messages.reset();
  }

  private void confirmCart() {
    messageBus.publish(List.of(new ConfirmShoppingCart(cartId, 1L)));
  }

  private void configure(PaymentGateway paymentGateway, boolean inStock) {
    messageBus.use(messages::catchMessage);

    eCommerce = ECommerceConfig.configure(
      eventStore,
      messageBus,
      item -> new PricedProductItem(item, unitPrice),
      paymentGateway,
      paymentTimeout,
      authorizationValidity,
      paymentRetryPeriod,
      fulfillmentService,
      item -> inStock,
      clock::get
    );
  }
}
