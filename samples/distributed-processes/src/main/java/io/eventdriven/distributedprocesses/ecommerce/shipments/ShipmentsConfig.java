package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.shipments.delivery.DeliveryProvider;
import io.eventdriven.distributedprocesses.ecommerce.shipments.delivery.DeliveryProviderClient;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEventForwarder;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentSummaries;
import io.eventdriven.distributedprocesses.ecommerce.shipments.reservation.ReservationExpiryWorker;
import io.eventdriven.distributedprocesses.ecommerce.shipments.reservation.StockReservations;

import java.time.Duration;
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
    DeliveryProvider deliveryProvider,
    Function<ProductItem, Boolean> isProductAvailable,
    Duration reservationValidity,
    Supplier<OffsetDateTime> now
  ) {
    var store = new AggregateStore<Shipment, ShipmentEvent, ShipmentId>(
      eventStore,
      ShipmentFacade::mapToStreamId,
      Shipment::empty
    );

    var facade = new ShipmentFacade(store, isProductAvailable, reservationValidity, now);

    messageBus
      .subscribe(ReserveStock.class, facade::reserveStock)
      .subscribe(SendPackage.class, facade::sendPackage)
      .subscribe(ConfirmParcelBooking.class, facade::confirmParcelBooking)
      .subscribe(DeliverPackage.class, facade::deliverPackage)
      .subscribe(ReleaseStock.class, facade::releaseStock)
      .subscribe(ExpireStockReservation.class, facade::expireStockReservation);

    var database = new Database();
    var summaries = new ShipmentSummaries(database);

    eventStore
      .subscribe(StockReserved.class, summaries::onStockReserved)
      .subscribe(ProductWasOutOfStock.class, summaries::onProductWasOutOfStock)
      .subscribe(PackageWasSent.class, summaries::onPackageWasSent)
      .subscribe(PackageWasDelivered.class, summaries::onPackageWasDelivered)
      .subscribe(StockReleased.class, summaries::onStockReleased)
      .subscribe(StockReservationExpired.class, summaries::onStockReservationExpired);

    var stockReservations = new StockReservations(database);

    eventStore
      .subscribe(StockReserved.class, stockReservations::onStockReserved)
      .subscribe(PackageWasSent.class, stockReservations::onPackageWasSent)
      .subscribe(StockReleased.class, stockReservations::onStockReleased)
      .subscribe(StockReservationExpired.class, stockReservations::onStockReservationExpired);

    var forwarder = new ShipmentExternalEventForwarder(summaries, messageBus);

    eventStore
      .subscribe(StockReserved.class, forwarder::onStockReserved)
      .subscribe(ProductWasOutOfStock.class, forwarder::onProductWasOutOfStock)
      .subscribe(PackageWasSent.class, forwarder::onPackageWasSent)
      .subscribe(PackageWasDelivered.class, forwarder::onPackageWasDelivered)
      .subscribe(StockReservationExpired.class, forwarder::onStockReservationExpired);

    var deliveryProviderClient = new DeliveryProviderClient(deliveryProvider);

    eventStore
      .subscribe(ParcelBookingRequested.class, envelope -> deliveryProviderClient.on(envelope.data()));

    return new ShipmentsModule(
      facade,
      new ReservationExpiryWorker(stockReservations, messageBus)
    );
  }
}
