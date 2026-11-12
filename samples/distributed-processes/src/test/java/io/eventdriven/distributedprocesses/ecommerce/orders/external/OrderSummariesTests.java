package io.eventdriven.distributedprocesses.ecommerce.orders.external;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.orders.Order;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCancellationReason;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderFacade;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderPaymentStatus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderShipmentStatus;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

public class OrderSummariesTests {
  private final OrderId orderId = OrderId.of(UUID.randomUUID());
  private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
  private final UUID clientId = UUID.randomUUID();
  private final PaymentId paymentId = PaymentId.derivedFrom(orderId.value());
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(orderId.value());
  private final PricedProductItem[] productItems = new PricedProductItem[]{
    new PricedProductItem(UUID.randomUUID(), 2, 12.5)
  };
  private final double totalPrice = 25;
  private final String carrier = "DHL";
  private final String trackingNumber = "JD014600006281234567";
  private final OffsetDateTime now = OffsetDateTime.now();

  private final Database database = new Database();
  private final OrderSummaries summaries = new OrderSummaries(database);

  @Test
  public void initializingStoresTheOrderWithBothParticipantsStillPending() {
    summaries.onInitialized(at(0, new OrderInitialized(orderId, cartId, clientId, productItems, totalPrice, now)));

    var summary = summaryOf(orderId);

    assertThat(summary.getOrderId()).isEqualTo(orderId);
    assertThat(summary.getCartId()).isEqualTo(cartId);
    assertThat(summary.getClientId()).isEqualTo(clientId);
    assertThat(summary.getProductItems()).usingRecursiveComparison().isEqualTo(productItems);
    assertThat(summary.getTotalPrice()).isEqualTo(totalPrice);
    assertThat(summary.getStatus()).isEqualTo(Order.Status.Opened);
    assertThat(summary.getPaymentId()).isNull();
    assertThat(summary.getShipmentId()).isNull();
    assertThat(summary.getPaymentStatus()).isEqualTo(OrderPaymentStatus.NotAuthorized);
    assertThat(summary.getShipmentStatus()).isEqualTo(OrderShipmentStatus.NotReserved);
  }

  @Test
  public void authorizationRecordsThePaymentItIsHeldOn() {
    initialize();

    summaries.onPaymentAuthorized(at(1, new OrderPaymentAuthorized(orderId, paymentId, now)));

    var summary = summaryOf(orderId);

    assertThat(summary.getPaymentId()).isEqualTo(paymentId);
    assertThat(summary.getPaymentStatus()).isEqualTo(OrderPaymentStatus.Authorized);
  }

  @Test
  public void stockReservationRecordsTheShipmentItIsHeldOn() {
    initialize();

    summaries.onStockReserved(at(1, new OrderStockReserved(orderId, shipmentId, now)));

    var summary = summaryOf(orderId);

    assertThat(summary.getShipmentId()).isEqualTo(shipmentId);
    assertThat(summary.getShipmentStatus()).isEqualTo(OrderShipmentStatus.Reserved);
  }

  @Test
  public void confirmationMarksTheOrderConfirmed() {
    confirm();

    assertThat(summaryOf(orderId).getStatus()).isEqualTo(Order.Status.Confirmed);
  }

  @Test
  public void captureMarksTheMoneyTaken() {
    confirm();

    summaries.onPaymentCaptured(at(4, new OrderPaymentCaptured(orderId, paymentId, totalPrice, now)));

    assertThat(summaryOf(orderId).getPaymentStatus()).isEqualTo(OrderPaymentStatus.Captured);
  }

  @Test
  public void dispatchMarksTheGoodsSent() {
    confirm();

    summaries.onPackageSent(at(4, new OrderPackageSent(orderId, paymentId, shipmentId, carrier, trackingNumber, now)));

    assertThat(summaryOf(orderId).getShipmentStatus()).isEqualTo(OrderShipmentStatus.Sent);
  }

  @Test
  public void paymentFailureLeavesNoHoldToRelease() {
    initialize();
    summaries.onPaymentAuthorized(at(1, new OrderPaymentAuthorized(orderId, paymentId, now)));

    summaries.onPaymentFailed(at(2, new OrderPaymentFailed(orderId, now)));

    assertThat(summaryOf(orderId).getPaymentStatus()).isEqualTo(OrderPaymentStatus.NotAuthorized);
  }

  @Test
  public void shipmentFailureLeavesNoReservationToRelease() {
    initialize();
    summaries.onStockReserved(at(1, new OrderStockReserved(orderId, shipmentId, now)));

    summaries.onShipmentFailed(at(2, new OrderShipmentFailed(orderId, now)));

    assertThat(summaryOf(orderId).getShipmentStatus()).isEqualTo(OrderShipmentStatus.NotReserved);
  }

  @Test
  public void completionMarksTheOrderCompleted() {
    confirm();

    summaries.onCompleted(at(4, new OrderCompleted(orderId, now)));

    assertThat(summaryOf(orderId).getStatus()).isEqualTo(Order.Status.Completed);
  }

  @Test
  public void cancellationKeepsWhereBothParticipantsStood() {
    confirm();

    summaries.onCancelled(at(4, new OrderCancelled(
      orderId,
      paymentId,
      OrderPaymentStatus.Authorized,
      shipmentId,
      OrderShipmentStatus.Reserved,
      OrderCancellationReason.Requested,
      now
    )));

    var summary = summaryOf(orderId);

    assertThat(summary.getStatus()).isEqualTo(Order.Status.Cancelled);
    assertThat(summary.getPaymentId()).isEqualTo(paymentId);
    assertThat(summary.getPaymentStatus()).isEqualTo(OrderPaymentStatus.Authorized);
    assertThat(summary.getShipmentId()).isEqualTo(shipmentId);
    assertThat(summary.getShipmentStatus()).isEqualTo(OrderShipmentStatus.Reserved);
  }

  @Test
  public void redeliveredEventDoesNotUndoWhatCameAfterIt() {
    initialize();
    var reservation = at(1, new OrderStockReserved(orderId, shipmentId, now));
    summaries.onStockReserved(reservation);
    summaries.onShipmentFailed(at(2, new OrderShipmentFailed(orderId, now)));

    summaries.onStockReserved(reservation);

    assertThat(summaryOf(orderId).getShipmentStatus()).isEqualTo(OrderShipmentStatus.NotReserved);
  }

  @Test
  public void anOrderThatWasNeverInitializedHasNoSummary() {
    assertThat(summaries.get(orderId)).isEmpty();
  }

  private void initialize() {
    summaries.onInitialized(at(0, new OrderInitialized(orderId, cartId, clientId, productItems, totalPrice, now)));
  }

  private void confirm() {
    initialize();
    summaries.onPaymentAuthorized(at(1, new OrderPaymentAuthorized(orderId, paymentId, now)));
    summaries.onStockReserved(at(2, new OrderStockReserved(orderId, shipmentId, now)));
    summaries.onConfirmed(at(3, new OrderConfirmed(orderId, paymentId, shipmentId, now)));
  }

  private OrderSummary summaryOf(OrderId orderId) {
    return summaries.get(orderId).orElseThrow();
  }

  private <Event> EventEnvelope<Event> at(long position, Event event) {
    return new EventEnvelope<>(event, new EventMetadata(OrderFacade.mapToStreamId(orderId), position, position));
  }
}
