package io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

import java.util.ArrayList;
import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.ConfirmPackageSent;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.DeliverPackage;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.RejectFulfillment;

// Stands in for a fulfillment service that answers when the test says so: that it sent the package
// or rejects the request, then that the package was delivered.
public class CallingBackFulfillmentService implements FulfillmentService {
  public static final String carrier = "UPS";
  public static final String trackingNumber = "1Z999AA10123456784";

  private final MessageBus messageBus;
  private final List<ShipmentId> requested = new ArrayList<>();
  private final List<ShipmentId> sent = new ArrayList<>();

  public CallingBackFulfillmentService(MessageBus messageBus) {
    this.messageBus = messageBus;
  }

  @Override
  public void request(ShipmentId shipmentId, ProductItem[] productItems) {
    requested.add(shipmentId);
  }

  public void sendPackages() {
    var confirmed = List.copyOf(requested);
    requested.clear();
    sent.addAll(confirmed);

    for (var shipmentId : confirmed) {
      messageBus.publish(List.of(new ConfirmPackageSent(shipmentId, carrier, trackingNumber)));
    }
  }

  public void rejectRequests() {
    var rejected = List.copyOf(requested);
    requested.clear();

    for (var shipmentId : rejected) {
      messageBus.publish(List.of(new RejectFulfillment(shipmentId)));
    }
  }

  public void deliverPackages() {
    var delivered = List.copyOf(sent);
    sent.clear();

    for (var shipmentId : delivered) {
      messageBus.publish(List.of(new DeliverPackage(shipmentId)));
    }
  }
}
