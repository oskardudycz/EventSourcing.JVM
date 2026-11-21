package io.eventdriven.distributedprocesses.ecommerce.shipments;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import io.eventdriven.distributedprocesses.core.messaging.RoutingTarget;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEventForwarder;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.FulfillmentService;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.FulfillmentServiceClient;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ShipmentsConfig {
    public static final String requests = "shipments-requests";
    public static final String responses = "shipments-responses";
    public static final String internal = "shipments-internal";

    private ShipmentsConfig() {}

    public static ShipmentsModule configure(
            EventStore eventStore,
            MessageBus messageBus,
            FulfillmentService fulfillmentService,
            Function<ProductItem, Boolean> isProductAvailable,
            Supplier<OffsetDateTime> now) {
        var store = new AggregateStore<Shipment, ShipmentEvent, ShipmentId>(
                eventStore, ShipmentFacade::mapToStreamId, Shipment::empty);

        var facade = new ShipmentFacade(store, isProductAvailable, now);

        messageBus
                .routes(new MessageRoutes()
                        .routeTarget(ReserveStock.class, command -> requests)
                        .routeTarget(RequestFulfillment.class, command -> requests)
                        .routeTarget(ReleaseStock.class, command -> requests)
                        .route(
                                ShipmentExternalEvent.class,
                                event -> new RoutingTarget(responses, Urns.routingKeyOf(event.referenceId())))
                        .routeTarget(MessageRoutes.inPackage(ShipmentsConfig.class.getPackageName()), internal)
                        .routeKey(ReserveStock.class, command -> Urns.routingKeyOf(command.referenceId()))
                        .routeKey(RequestFulfillment.class, command -> routingKeyOf(command.shipmentId()))
                        .routeKey(ConfirmPackageSent.class, command -> routingKeyOf(command.shipmentId()))
                        .routeKey(RejectFulfillment.class, command -> routingKeyOf(command.shipmentId()))
                        .routeKey(DeliverPackage.class, command -> routingKeyOf(command.shipmentId()))
                        .routeKey(ReleaseStock.class, command -> routingKeyOf(command.shipmentId())))
                .channel(requests)
                .channel(responses)
                .channel(internal)
                .bind(requests, requests, "#")
                .bind(internal, internal, "#")
                .subscribe(requests, ReserveStock.class, facade::reserveStock)
                .subscribe(requests, RequestFulfillment.class, facade::requestFulfillment)
                .subscribe(requests, ReleaseStock.class, facade::releaseStock)
                .subscribe(internal, ConfirmPackageSent.class, facade::confirmPackageSent)
                .subscribe(internal, RejectFulfillment.class, facade::rejectFulfillment)
                .subscribe(internal, DeliverPackage.class, facade::deliverPackage);

        var forwarder = new ShipmentExternalEventForwarder(messageBus);

        eventStore.subscribe(
                "shipments-external-forwarder",
                List.of(
                        StockReserved.class,
                        ProductWasOutOfStock.class,
                        PackageWasSent.class,
                        FulfillmentRejected.class),
                forwarder::handle);

        var fulfillmentServiceClient = new FulfillmentServiceClient(fulfillmentService);

        eventStore.subscribe(
                "fulfillment-service-client",
                List.of(FulfillmentRequested.class),
                envelope -> fulfillmentServiceClient.on((FulfillmentRequested) envelope.data()));

        return new ShipmentsModule(facade);
    }

    private static String routingKeyOf(ShipmentId shipmentId) {
        return Urns.routingKeyOf(shipmentId.value());
    }
}
