package io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class FulfillmentServiceClientTests {

  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(referenceId);
  private final ProductItem availableItem = new ProductItem(UUID.randomUUID(), 2);
  private final ProductItem unavailableItem = new ProductItem(UUID.randomUUID(), 1);
  private final ProductItem[] available = new ProductItem[]{availableItem};
  private final Function<ProductItem, Boolean> isProductAvailable =
    item -> Set.of(availableItem).contains(item);
  private final OffsetDateTime now = OffsetDateTime.now();

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
  private final MessageCatcher published = new MessageCatcher();

  @Test
  public void callingBackServiceSendsThenDeliversThePackage() {
    var fulfillmentService = new CallingBackFulfillmentService(messageBus);
    configure(fulfillmentService);

    messageBus.publish(List.of(new ReserveStock(referenceId, available)));
    messageBus.publish(List.of(new RequestFulfillment(shipmentId)));
    published.reset();

    fulfillmentService.sendPackages();
    fulfillmentService.deliverPackages();

    published.shouldReceiveMessages(
      new PackageWasSent(
        shipmentId,
        referenceId,
        CallingBackFulfillmentService.carrier,
        CallingBackFulfillmentService.trackingNumber,
        now
      ),
      new PackageWasDelivered(shipmentId, referenceId, now)
    );
  }

  @Test
  public void silentServiceLeavesThePackageWaitingForFulfillment() {
    configure(new SilentFulfillmentService());

    messageBus.publish(List.of(new ReserveStock(referenceId, available)));
    published.reset();

    messageBus.publish(List.of(new RequestFulfillment(shipmentId)));

    published.shouldReceiveMessages(
      new FulfillmentRequested(shipmentId, referenceId, available, now)
    );
  }

  @Test
  public void unreachableServiceLeavesTheStockHeldInsteadOfThrowing() {
    configure(new ThrowingFulfillmentService());

    messageBus.publish(List.of(new ReserveStock(referenceId, available)));
    published.reset();

    assertThatCode(() -> messageBus.publish(List.of(new RequestFulfillment(shipmentId))))
      .doesNotThrowAnyException();

    messageBus.publish(List.of(new ReleaseStock(shipmentId)));

    published.shouldReceiveMessages(
      new FulfillmentRequested(shipmentId, referenceId, available, now),
      new StockReleased(shipmentId, referenceId, now)
    );
  }

  @Test
  public void serviceIsAskedWithTheShipmentAndItsReservedItems() {
    var requests = new ArrayList<String>();
    configure((requestedShipmentId, productItems) ->
      requests.add("%s:%s".formatted(requestedShipmentId, productItems.length))
    );

    messageBus.publish(List.of(new ReserveStock(referenceId, available)));
    messageBus.publish(List.of(new RequestFulfillment(shipmentId)));

    assertThat(requests).containsExactly("%s:%s".formatted(shipmentId, available.length));
  }

  @Test
  public void requestingFulfillmentTwiceAsksTheServiceOnce() {
    var asked = new ArrayList<ShipmentId>();
    configure((requestedShipmentId, productItems) -> asked.add(requestedShipmentId));

    messageBus.publish(List.of(new ReserveStock(referenceId, available)));
    messageBus.publish(List.of(new RequestFulfillment(shipmentId), new RequestFulfillment(shipmentId)));

    assertThat(asked).containsExactly(shipmentId);
  }

  @Test
  public void aReservationIsNeverSentToTheServiceBeforeFulfillmentIsRequested() {
    var asked = new ArrayList<ShipmentId>();
    configure((requestedShipmentId, productItems) -> asked.add(requestedShipmentId));

    messageBus.publish(List.of(new ReserveStock(referenceId, available)));

    assertThat(asked).isEmpty();
  }

  @Test
  public void outOfStockShipmentIsNeverSentToTheService() {
    var asked = new ArrayList<ShipmentId>();
    configure((requestedShipmentId, productItems) -> asked.add(requestedShipmentId));

    messageBus.publish(List.of(new ReserveStock(referenceId, new ProductItem[]{unavailableItem})));
    messageBus.publish(List.of(new RequestFulfillment(shipmentId)));

    assertThat(asked).isEmpty();
  }

  @Test
  public void callingBackServiceCanRejectTheRequest() {
    var fulfillmentService = new CallingBackFulfillmentService(messageBus);
    configure(fulfillmentService);

    messageBus.publish(List.of(new ReserveStock(referenceId, available)));
    messageBus.publish(List.of(new RequestFulfillment(shipmentId)));
    published.reset();

    fulfillmentService.rejectRequests();

    published.shouldReceiveMessages(new FulfillmentRejected(shipmentId, referenceId, now));
  }

  private void configure(FulfillmentService fulfillmentService) {
    eventStore.use(published::catchMessage);

    ShipmentsConfig.configure(
      eventStore,
      messageBus,
      fulfillmentService,
      isProductAvailable,
      () -> now
    );
  }
}
