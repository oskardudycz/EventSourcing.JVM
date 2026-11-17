package io.eventdriven.distributedprocesses.ecommerce;

import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.kurrentdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderFacade;
import io.eventdriven.distributedprocesses.ecommerce.orders.PaymentTerms;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.CustomerOrders;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatuses;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.ScheduledUnpaidOrdersWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentFacade;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentValidity;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.ScheduledAuthorizationExpiryWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.ScheduledPaymentTimeoutWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.sandbox.SandboxPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.FulfillmentService;
import io.eventdriven.distributedprocesses.ecommerce.shipments.sandbox.SandboxFulfillmentService;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.CustomerShoppingCarts;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api.Carts;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.function.Supplier;

@Configuration
@EnableScheduling
public class ECommerceApplicationConfig {
  @Bean
  OpenAPI openApi() {
    return new OpenAPI().info(new Info().title("E-Commerce").version("1.0.0"));
  }

  @Bean
  GlobalOperationCustomizer eventCatalogOperationTypes() {
    return (operation, handlerMethod) -> {
      operation.addExtension(
        "x-eventcatalog-message-type",
        handlerMethod.hasMethodAnnotation(GetMapping.class) ? "query" : "command"
      );
      return operation;
    };
  }

  @Bean
  Supplier<OffsetDateTime> clock() {
    return OffsetDateTime::now;
  }

  @Bean
  EventStore eventStore() {
    return new InMemoryEventStore();
  }

  @Bean
  MessageBus messageBus() {
    return new InMemoryMessageBus();
  }

  @Bean
  SandboxPaymentGateway sandboxPaymentGateway(MessageBus messageBus, Supplier<OffsetDateTime> clock) {
    return new SandboxPaymentGateway(messageBus, clock);
  }

  @Bean
  SandboxFulfillmentService sandboxFulfillmentService(MessageBus messageBus) {
    return new SandboxFulfillmentService(messageBus);
  }

  @Bean
  ECommerceModule eCommerce(
    EventStore eventStore,
    MessageBus messageBus,
    ProductPriceCalculator prices,
    PaymentGateway paymentGateway,
    FulfillmentService fulfillmentService,
    Supplier<OffsetDateTime> clock
  ) {
    return ECommerceConfig.configure(
      eventStore,
      messageBus,
      prices,
      paymentGateway,
      PaymentValidity.of(Duration.ofHours(24)),
      PaymentTerms.within(Duration.ofDays(7)),
      fulfillmentService,
      productItem -> true,
      clock
    );
  }

  @Bean
  ShoppingCartFacade shoppingCartFacade(ECommerceModule eCommerce) {
    return eCommerce.shoppingCarts().facade();
  }

  @Bean
  CustomerShoppingCarts customerShoppingCarts(ECommerceModule eCommerce) {
    return eCommerce.shoppingCarts().customerShoppingCarts();
  }

  @Bean
  Carts carts(ECommerceModule eCommerce) {
    return eCommerce.shoppingCarts().carts();
  }

  @Bean
  OrderFacade orderFacade(ECommerceModule eCommerce) {
    return eCommerce.orders().facade();
  }

  @Bean
  CustomerOrders customerOrders(ECommerceModule eCommerce) {
    return eCommerce.orders().customerOrders();
  }

  @Bean
  OrderStatuses orderStatuses(ECommerceModule eCommerce) {
    return eCommerce.orders().orderStatuses();
  }

  @Bean
  PaymentFacade paymentFacade(ECommerceModule eCommerce) {
    return eCommerce.payments().facade();
  }

  @Bean
  ScheduledPaymentTimeoutWorker paymentTimeoutWorker(ECommerceModule eCommerce) {
    return eCommerce.payments().timeoutWorker();
  }

  @Bean
  ScheduledAuthorizationExpiryWorker authorizationExpiryWorker(ECommerceModule eCommerce) {
    return eCommerce.payments().authorizationExpiryWorker();
  }

  @Bean
  ScheduledUnpaidOrdersWorker unpaidOrdersWorker(ECommerceModule eCommerce) {
    return eCommerce.orders().unpaidOrdersWorker();
  }
}
