package io.eventdriven.ecommerce.shipments.sandbox;

import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shipments.ShipmentCommand.ConfirmPackageSent;
import io.eventdriven.ecommerce.shipments.ShipmentCommand.DeliverPackage;
import io.eventdriven.ecommerce.shipments.ShipmentCommand.RejectFulfillment;
import io.eventdriven.ecommerce.shipments.fulfillment.FulfillmentService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stands in for the warehouse and its fulfillment service when the application runs. It keeps the stock and the
 * packages it was asked to send, and a person decides on the sandbox warehouse page what happens to each package.
 */
public class SandboxWarehouse implements FulfillmentService {
    public static final String carrier = "Sandbox Express";

    public enum Status {
        Waiting,
        Sent,
        Rejected,
        Delivered
    }

    public record Package(
            Shipment.Id shipmentId, Shipment.ProductItem[] productItems, Status status, Instant requestedAt) {}

    private final MessageBus messageBus;
    private final Set<UUID> outOfStock = ConcurrentHashMap.newKeySet();
    private final Map<Shipment.Id, Package> packages = new ConcurrentHashMap<>();

    public SandboxWarehouse(MessageBus messageBus, Set<UUID> outOfStock) {
        this.messageBus = messageBus;
        this.outOfStock.addAll(outOfStock);
    }

    public boolean isInStock(Shipment.ProductItem productItem) {
        return isInStock(productItem.productId());
    }

    public boolean isInStock(UUID productId) {
        return !outOfStock.contains(productId);
    }

    public void stock(UUID productId, boolean inStock) {
        if (inStock) outOfStock.remove(productId);
        else outOfStock.add(productId);
    }

    @Override
    public void request(Shipment.Id shipmentId, Shipment.ProductItem[] productItems) {
        packages.putIfAbsent(shipmentId, new Package(shipmentId, productItems, Status.Waiting, Instant.now()));
    }

    public List<Package> packages() {
        return packages.values().stream()
                .sorted(Comparator.comparing(Package::requestedAt).reversed())
                .toList();
    }

    public Optional<Package> find(Shipment.Id shipmentId) {
        return Optional.ofNullable(packages.get(shipmentId));
    }

    public void send(Shipment.Id shipmentId) {
        if (moved(shipmentId, Status.Waiting, Status.Sent)) {
            var trackingNumber =
                    "SBX%010d".formatted(ThreadLocalRandom.current().nextLong(10_000_000_000L));
            messageBus.publish(List.of(new ConfirmPackageSent(shipmentId, carrier, trackingNumber)));
        }
    }

    public void reject(Shipment.Id shipmentId) {
        if (moved(shipmentId, Status.Waiting, Status.Rejected))
            messageBus.publish(List.of(new RejectFulfillment(shipmentId)));
    }

    public void deliver(Shipment.Id shipmentId) {
        if (moved(shipmentId, Status.Sent, Status.Delivered))
            messageBus.publish(List.of(new DeliverPackage(shipmentId)));
    }

    private boolean moved(Shipment.Id shipmentId, Status from, Status to) {
        var current = packages.get(shipmentId);
        return current != null
                && current.status() == from
                && packages.replace(
                        shipmentId,
                        current,
                        new Package(shipmentId, current.productItems(), to, current.requestedAt()));
    }
}
