package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.FulfillmentService;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.FulfillmentServiceClient;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEventForwarder;

import java.time.OffsetDateTime;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;

public final class ShipmentsConfig {
  private ShipmentsConfig() {
  }

  public static ShipmentsModule configure(
    EventStore eventStore,
    MessageBus messageBus,
    FulfillmentService fulfillmentService,
    Function<ProductItem, Boolean> isProductAvailable,
    Supplier<OffsetDateTime> now
  ) {
    var store = new AggregateStore<Shipment, ShipmentEvent, ShipmentId>(
      eventStore,
      ShipmentFacade::mapToStreamId,
      Shipment::empty
    );

    var facade = new ShipmentFacade(store, isProductAvailable, now);

    messageBus
      .subscribe(ReserveStock.class, facade::reserveStock)
      .subscribe(RequestFulfillment.class, facade::requestFulfillment)
      .subscribe(ConfirmPackageSent.class, facade::confirmPackageSent)
      .subscribe(RejectFulfillment.class, facade::rejectFulfillment)
      .subscribe(DeliverPackage.class, facade::deliverPackage)
      .subscribe(ReleaseStock.class, facade::releaseStock);

    var forwarder = new ShipmentExternalEventForwarder(messageBus);

    eventStore
      .subscribe(StockReserved.class, forwarder::onStockReserved)
      .subscribe(ProductWasOutOfStock.class, forwarder::onProductWasOutOfStock)
      .subscribe(PackageWasSent.class, forwarder::onPackageWasSent)
      .subscribe(FulfillmentRejected.class, forwarder::onFulfillmentRejected);

    var fulfillmentServiceClient = new FulfillmentServiceClient(fulfillmentService);

    eventStore
      .subscribe(FulfillmentRequested.class, envelope -> fulfillmentServiceClient.on(envelope.data()));

    return new ShipmentsModule(facade);
  }
}
