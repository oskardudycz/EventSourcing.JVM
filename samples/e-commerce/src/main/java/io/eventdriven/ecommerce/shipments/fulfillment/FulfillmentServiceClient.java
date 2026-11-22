package io.eventdriven.ecommerce.shipments.fulfillment;

import static io.eventdriven.ecommerce.shipments.ShipmentEvent.FulfillmentRequested;

import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.processing.Reactor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FulfillmentServiceClient implements Reactor {
    private static final Logger logger = LoggerFactory.getLogger(FulfillmentServiceClient.class);

    private final FulfillmentService fulfillmentService;

    public FulfillmentServiceClient(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(FulfillmentRequested.class);
    }

    @Override
    public void handle(MessageEnvelope<Object> envelope) {
        switch (envelope.message()) {
            case FulfillmentRequested event -> on(event);
            default -> throw new IllegalArgumentException("Unexpected message %s".formatted(envelope.message()));
        }
    }

    public void on(FulfillmentRequested event) {
        try {
            fulfillmentService.request(event.shipmentId(), event.productItems());
        } catch (Exception failedRequest) {
            // Nothing is recorded here: the order is canceled when its payment authorization expires.
            logger.error("Requesting fulfillment for shipment %s failed".formatted(event.shipmentId()), failedRequest);
        }
    }
}
