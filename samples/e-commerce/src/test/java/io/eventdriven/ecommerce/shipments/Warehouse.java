package io.eventdriven.ecommerce.shipments;

import static io.eventdriven.testing.api.ApiE2ESpecification.publish;

import io.eventdriven.ecommerce.shipments.ShipmentCommand.ConfirmPackageSent;
import io.eventdriven.ecommerce.shipments.ShipmentCommand.DeliverPackage;
import io.eventdriven.ecommerce.shipments.ShipmentCommand.RejectFulfillment;
import io.eventdriven.ecommerce.shipments.fulfillment.FulfillmentService;
import io.eventdriven.testing.api.ApiE2ESpecification.TestAction;
import io.eventdriven.testing.api.ApiE2ESpecification.TestStep;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Warehouse implements FulfillmentService {
    public static final String carrier = "UPS";
    public static final String trackingNumber = "1Z999AA10123456784";

    private record Request(Shipment.Id shipmentId, List<Shipment.ProductItem> productItems) {}

    private final Set<UUID> outOfStock = ConcurrentHashMap.newKeySet();
    private final Set<Shipment.Id> unreachable = ConcurrentHashMap.newKeySet();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    public TestStep isOutOf(UUID productId) {
        return (TestAction) () -> outOfStock.add(productId);
    }

    public TestStep isUnreachableFor(Shipment.Id shipmentId) {
        return (TestAction) () -> unreachable.add(shipmentId);
    }

    public boolean isAvailable(Shipment.ProductItem productItem) {
        return !outOfStock.contains(productItem.productId());
    }

    public boolean wasAskedToSend(Shipment.Id shipmentId) {
        return !requestsFor(shipmentId).isEmpty();
    }

    public List<List<Shipment.ProductItem>> requestsFor(Shipment.Id shipmentId) {
        return requests.stream()
                .filter(request -> request.shipmentId().equals(shipmentId))
                .map(Request::productItems)
                .toList();
    }

    public static TestStep sendsThePackage(Shipment.Id shipmentId) {
        return publish(new ConfirmPackageSent(shipmentId, carrier, trackingNumber));
    }

    public static TestStep rejectsTheRequest(Shipment.Id shipmentId) {
        return publish(new RejectFulfillment(shipmentId));
    }

    public static TestStep deliversThePackage(Shipment.Id shipmentId) {
        return publish(new DeliverPackage(shipmentId));
    }

    @Override
    public void request(Shipment.Id shipmentId, Shipment.ProductItem[] productItems) {
        if (unreachable.contains(shipmentId)) throw new RuntimeException("Warehouse is unreachable");

        requests.add(new Request(shipmentId, List.of(productItems)));
    }
}
