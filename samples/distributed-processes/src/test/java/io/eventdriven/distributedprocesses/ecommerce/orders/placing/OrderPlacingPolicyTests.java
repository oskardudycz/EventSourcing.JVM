package io.eventdriven.distributedprocesses.ecommerce.orders.placing;

import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;

public class OrderPlacingPolicyTests {
  private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
  private final OrderId orderId = OrderId.derivedFrom(cartId.value());
  private final PaymentId paymentId = PaymentId.derivedFrom(orderId.value());
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(orderId.value());
  private final UUID clientId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();
  private final double totalPrice = 25;
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime expiresAt = now.plusDays(7);
  private final OffsetDateTime reservedUntil = now.plusHours(2);

  private final PricedProductItem[] orderItems = new PricedProductItem[]{
    new PricedProductItem(productId, 2, 12.5)
  };

  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final MessageCatcher sent = new MessageCatcher();
  private final OrderPlacingPolicy policy = new OrderPlacingPolicy(commandBus);

  public OrderPlacingPolicyTests() {
    commandBus.use(sent::catchMessage);

    // Every command the policy can send needs a handler, or the bus refuses it.
    commandBus
      .handle(InitializeOrder.class, _ -> {
      })
      .handle(RecordOrderPaymentAuthorization.class, _ -> {
      })
      .handle(RecordOrderStockReservation.class, _ -> {
      })
      .handle(PaymentCommand.AuthorizePayment.class, _ -> {
      })
      .handle(ShipmentCommand.ReserveStock.class, _ -> {
      });
  }

  @Test
  public void aFinalizedCartStartsAnOrderWhoseIdIsDerivedFromTheCart() {
    policy.on(new ShoppingCartExternalEvent.ShoppingCartFinalized(cartId, clientId, cartItems(), totalPrice, now));

    sent.shouldReceiveMessages(
      new InitializeOrder(orderId, cartId, clientId, orderItems, totalPrice)
    );
  }

  @Test
  public void anInitializedOrderTakesBothHoldsTogether() {
    policy.on(orderInitialized());

    sent.shouldReceiveMessages(
      new PaymentCommand.AuthorizePayment(orderId.value(), totalPrice),
      new ShipmentCommand.ReserveStock(orderId.value(), shipmentItems())
    );
  }

  @Test
  public void anAuthorizedPaymentIsRecordedAgainstTheOrderTheReferenceNames() {
    policy.on(new PaymentExternalEvent.PaymentAuthorized(
      orderId.value(), paymentId, totalPrice, now, expiresAt
    ));

    sent.shouldReceiveMessages(new RecordOrderPaymentAuthorization(orderId, paymentId, now));
  }

  @Test
  public void reservedStockIsRecordedAgainstTheOrder() {
    policy.on(new ShipmentExternalEvent.StockReserved(
      shipmentId, orderId.value(), shipmentItems(), now, reservedUntil
    ));

    sent.shouldReceiveMessages(new RecordOrderStockReservation(orderId, shipmentId, now));
  }

  @Test
  public void handlingTheSameEventTwiceSendsTwoIdenticalCommands() {
    policy.on(orderInitialized());
    policy.on(orderInitialized());

    var authorizePayment = new PaymentCommand.AuthorizePayment(orderId.value(), totalPrice);
    var reserveStock = new ShipmentCommand.ReserveStock(orderId.value(), shipmentItems());

    sent.shouldReceiveMessages(authorizePayment, reserveStock, authorizePayment, reserveStock);
  }

  private OrderEvent.OrderInitialized orderInitialized() {
    return new OrderEvent.OrderInitialized(
      orderId, cartId, clientId, orderItems, totalPrice, now
    );
  }

  private ProductItem[] shipmentItems() {
    return new ProductItem[]{new ProductItem(productId, 2)};
  }

  private io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem[] cartItems() {
    return new io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem[]{
      new io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem(
        new io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem(productId, 2),
        12.5
      )
    };
  }
}
