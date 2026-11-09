package io.eventdriven.distributedprocesses.ecommerce.orders.fulfilling;

import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;

public class OrderFulfillingPolicyTests {
  private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
  private final OrderId orderId = OrderId.derivedFrom(cartId.value());
  private final PaymentId paymentId = PaymentId.derivedFrom(orderId.value());
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(orderId.value());
  private final UUID productId = UUID.randomUUID();
  private final double totalPrice = 25;
  private final String carrier = "DHL";
  private final String trackingNumber = "JD014600006281234567";
  private final OffsetDateTime now = OffsetDateTime.now();

  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final MessageCatcher sent = new MessageCatcher();
  private final OrderFulfillingPolicy policy = new OrderFulfillingPolicy(commandBus);

  public OrderFulfillingPolicyTests() {
    commandBus.use(sent::catchMessage);

    // Every command the policy can send needs a handler, or the bus refuses it.
    commandBus
      .handle(RecordOrderPaymentCapture.class, _ -> {
      })
      .handle(RecordOrderPackageSent.class, _ -> {
      })
      .handle(RecordOrderDelivery.class, _ -> {
      })
      .handle(PaymentCommand.CapturePayment.class, _ -> {
      })
      .handle(ShipmentCommand.SendPackage.class, _ -> {
      });
  }

  @Test
  public void aConfirmedOrderSendsThePackageBeforeAnyMoneyMoves() {
    policy.on(new OrderEvent.OrderConfirmed(orderId, paymentId, shipmentId, now));

    sent.shouldReceiveMessages(new ShipmentCommand.SendPackage(shipmentId));
  }

  @Test
  public void aSentPackageIsRecordedAgainstTheOrder() {
    policy.on(new ShipmentExternalEvent.PackageWasSent(
      shipmentId, orderId.value(), shipmentItems(), carrier, trackingNumber, now
    ));

    sent.shouldReceiveMessages(new RecordOrderPackageSent(orderId, carrier, trackingNumber, now));
  }

  @Test
  public void theOrdersOwnDispatchEventIsWhatCapturesThePayment() {
    policy.on(new OrderEvent.OrderPackageSent(
      orderId, paymentId, shipmentId, carrier, trackingNumber, now
    ));

    sent.shouldReceiveMessages(new PaymentCommand.CapturePayment(paymentId));
  }

  @Test
  public void aCapturedPaymentIsRecordedAgainstTheOrder() {
    policy.on(new PaymentExternalEvent.PaymentCaptured(
      orderId.value(), paymentId, totalPrice, now
    ));

    sent.shouldReceiveMessages(new RecordOrderPaymentCapture(orderId, now));
  }

  @Test
  public void aDeliveredPackageIsRecordedAgainstTheOrder() {
    policy.on(new ShipmentExternalEvent.PackageWasDelivered(shipmentId, orderId.value(), now));

    sent.shouldReceiveMessages(new RecordOrderDelivery(orderId, now));
  }

  private ProductItem[] shipmentItems() {
    return new ProductItem[]{new ProductItem(productId, 2)};
  }
}
