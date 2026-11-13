package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.SilentFulfillmentService;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class ShipmentFacadeTests {

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
  private final String carrier = "DHL";
  private final String trackingNumber = "JD014600006281234567";

  public ShipmentFacadeTests() {
    eventStore.use(published::catchMessage);
    ShipmentsConfig.configure(
      eventStore,
      messageBus,
      new SilentFulfillmentService(),
      isProductAvailable,
      () -> now
    );
  }

  @Test
  public void reserveStockWithEverythingAvailableDispatchesAndStoresStockReserved() {
    messageBus.publish(List.of(new ReserveStock(referenceId, available)));

    var reserved = new StockReserved(shipmentId, referenceId, available, now);

    published.shouldReceiveMessages(reserved);
    assertThat(storedEvents()).usingRecursiveComparison().isEqualTo(List.of(reserved));
  }

  @Test
  public void reserveStockWithAnUnavailableProductDispatchesProductWasOutOfStock() {
    var productItems = new ProductItem[]{availableItem, unavailableItem};

    messageBus.publish(List.of(new ReserveStock(referenceId, productItems)));

    published.shouldReceiveMessages(
      new ProductWasOutOfStock(shipmentId, referenceId, productItems, now)
    );
  }

  @Test
  public void requestFulfillmentDispatchesFulfillmentRequestedWithTheReservedItems() {
    reserve();

    messageBus.publish(List.of(new RequestFulfillment(shipmentId)));

    published.shouldReceiveMessages(
      new FulfillmentRequested(shipmentId, referenceId, available, now)
    );
  }

  @Test
  public void confirmPackageSentDispatchesPackageWasSentWithTheCarrierAndTrackingNumber() {
    reserve();
    messageBus.publish(List.of(new RequestFulfillment(shipmentId)));
    published.reset();

    messageBus.publish(List.of(new ConfirmPackageSent(shipmentId, carrier, trackingNumber)));

    published.shouldReceiveMessages(
      new PackageWasSent(shipmentId, referenceId, carrier, trackingNumber, now)
    );
  }

  @Test
  public void requestFulfillmentWithoutAReservationStoresNothingAndDoesNotThrow() {
    assertThatCode(() -> messageBus.publish(List.of(new RequestFulfillment(shipmentId))))
      .doesNotThrowAnyException();

    published.shouldNotReceiveAnyEvent();
    assertThat(storedEvents()).isEmpty();
  }

  @Test
  public void deliverPackageDispatchesPackageWasDelivered() {
    reserve();
    dispatch();

    messageBus.publish(List.of(new DeliverPackage(shipmentId)));

    published.shouldReceiveMessages(new PackageWasDelivered(shipmentId, referenceId, now));
  }

  @Test
  public void deliveringAnUnsentShipmentStoresNothingAndDoesNotThrow() {
    reserve();

    assertThatCode(() -> messageBus.publish(List.of(new DeliverPackage(shipmentId))))
      .doesNotThrowAnyException();

    published.shouldNotReceiveAnyEvent();
  }

  @Test
  public void releaseStockDispatchesStockReleased() {
    reserve();

    messageBus.publish(List.of(new ReleaseStock(shipmentId)));

    published.shouldReceiveMessages(new StockReleased(shipmentId, referenceId, now));
  }

  @Test
  public void releasingStockOfASentPackageStoresNothingAndDoesNotThrow() {
    reserve();
    dispatch();

    assertThatCode(() -> messageBus.publish(List.of(new ReleaseStock(shipmentId))))
      .doesNotThrowAnyException();

    published.shouldNotReceiveAnyEvent();
  }


  @Test
  public void rejectFulfillmentDispatchesFulfillmentRejected() {
    reserve();
    messageBus.publish(List.of(new RequestFulfillment(shipmentId)));
    published.reset();

    messageBus.publish(List.of(new RejectFulfillment(shipmentId)));

    published.shouldReceiveMessages(new FulfillmentRejected(shipmentId, referenceId, now));
  }

  @Test
  public void reserveStockDerivesTheShipmentIdFromTheReferenceSoASecondReservationChangesNothing() {
    messageBus.publish(List.of(
      new ReserveStock(referenceId, available),
      new ReserveStock(referenceId, available)
    ));

    assertThat(storedEvents()).usingRecursiveComparison().isEqualTo(
      List.of(new StockReserved(shipmentId, referenceId, available, now))
    );
  }

  @Test
  public void mapsIdToItsOwnStream() {
    assertThat(ShipmentFacade.mapToStreamId(shipmentId))
      .isEqualTo("Shipment-%s".formatted(shipmentId.tail()));
  }

  private void reserve() {
    messageBus.publish(List.of(new ReserveStock(referenceId, available)));
    published.reset();
  }

  private void dispatch() {
    messageBus.publish(List.of(
      new RequestFulfillment(shipmentId),
      new ConfirmPackageSent(shipmentId, carrier, trackingNumber)
    ));
    published.reset();
  }

  private List<Object> storedEvents() {
    return switch (eventStore.read(ShipmentFacade.mapToStreamId(shipmentId))) {
      case EventStore.ReadResult.Success success -> List.of(success.events());
      default -> List.of();
    };
  }
}
