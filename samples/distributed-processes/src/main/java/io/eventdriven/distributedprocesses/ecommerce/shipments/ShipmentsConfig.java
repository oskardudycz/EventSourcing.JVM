package io.eventdriven.distributedprocesses.ecommerce.shipments;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;

import io.eventdriven.distributedprocesses.core.commands.CommandHandler;
import io.eventdriven.distributedprocesses.core.eventstore.EventStore;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageChannel;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.distributedprocesses.core.messaging.MessageQueue;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import io.eventdriven.distributedprocesses.core.messaging.RoutingTarget;
import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageMapping;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEventForwarder;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentSummaries;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentSummaryRepository;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.FulfillmentService;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.FulfillmentServiceClient;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShipmentsConfig {
    public static final String requests = "shipments-requests";
    public static final String responses = "shipments-responses";
    public static final String internal = "shipments-internal";

    @Bean("shipmentsMapping")
    public static MessageMapping mapping() {
        return new MessageMapping()
                .trust(ShipmentsConfig.class.getPackageName(), ShipmentExternalEvent.class.getPackageName());
    }

    @Bean
    public static MessageRoutes shipmentsRoutes() {
        return new MessageRoutes()
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
                .routeKey(ReleaseStock.class, command -> routingKeyOf(command.shipmentId()));
    }

    @Bean
    public static MessageChannel shipmentsRequestsChannel() {
        return new MessageChannel(requests);
    }

    @Bean
    public static MessageChannel shipmentsResponsesChannel() {
        return new MessageChannel(responses);
    }

    @Bean
    public static MessageChannel shipmentsInternalChannel() {
        return new MessageChannel(internal);
    }

    @Bean
    public static MessageQueue shipmentsRequestsQueue() {
        return new MessageQueue(requests, requests, "#");
    }

    @Bean
    public static MessageQueue shipmentsInternalQueue() {
        return new MessageQueue(internal, internal, "#");
    }

    @Bean
    public Function<ProductItem, Boolean> isProductAvailable() {
        return productItem -> true;
    }

    @Bean
    public ShipmentFacade shipmentFacade(
            EventStore eventStore,
            MessageContext messageContext,
            MessageBus messageBus,
            Function<ProductItem, Boolean> isProductAvailable,
            Supplier<OffsetDateTime> now) {
        var decider = new ShipmentDecider(isProductAvailable, now);
        var commandHandler = new CommandHandler<ShipmentId, Shipment, ShipmentCommand, ShipmentEvent>(
                eventStore,
                messageContext,
                ShipmentFacade::mapToStreamId,
                Shipment::initial,
                Shipment::evolve,
                decider::handle);

        var facade = new ShipmentFacade(commandHandler);

        messageBus
                .subscribe(requests, ReserveStock.class, facade::reserveStock)
                .subscribe(requests, RequestFulfillment.class, facade::requestFulfillment)
                .subscribe(requests, ReleaseStock.class, facade::releaseStock)
                .subscribe(internal, ConfirmPackageSent.class, facade::confirmPackageSent)
                .subscribe(internal, RejectFulfillment.class, facade::rejectFulfillment)
                .subscribe(internal, DeliverPackage.class, facade::deliverPackage);

        return facade;
    }

    @Bean
    public ShipmentSummaries shipmentSummaries(
            EventStore eventStore, ShipmentSummaryRepository shipmentSummaryRepository) {
        var shipmentSummaries = new ShipmentSummaries(shipmentSummaryRepository);

        eventStore.subscribe("shipment-summaries", shipmentSummaries.canHandle(), shipmentSummaries::handle);

        return shipmentSummaries;
    }

    @Bean
    public ShipmentExternalEventForwarder shipmentExternalEventForwarder(
            EventStore eventStore, ShipmentSummaries shipmentSummaries, MessageBus messageBus) {
        var forwarder = new ShipmentExternalEventForwarder(shipmentSummaries, messageBus);

        eventStore.subscribe(
                "shipments-external-forwarder",
                List.of(
                        StockReserved.class,
                        ProductWasOutOfStock.class,
                        PackageWasSent.class,
                        FulfillmentRejected.class),
                forwarder::handle);

        return forwarder;
    }

    @Bean
    public FulfillmentServiceClient fulfillmentServiceClient(
            EventStore eventStore, FulfillmentService fulfillmentService) {
        var fulfillmentServiceClient = new FulfillmentServiceClient(fulfillmentService);

        eventStore.subscribe(
                "fulfillment-service-client",
                List.of(FulfillmentRequested.class),
                envelope -> fulfillmentServiceClient.on((FulfillmentRequested) envelope.data()));

        return fulfillmentServiceClient;
    }

    private static String routingKeyOf(ShipmentId shipmentId) {
        return Urns.routingKeyOf(shipmentId.value());
    }
}
