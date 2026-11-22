package io.eventdriven.ecommerce.shipments.external;

import static io.eventdriven.ecommerce.shipments.ShipmentEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shipments.ShipmentFacade;
import io.eventdriven.testing.messaging.Metadata;
import io.eventdriven.testing.projections.ProjectionsDatabase;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

@ParameterizedClass
@EnumSource(ProjectionsDatabase.class)
public class ShipmentSummariesTests {
    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final Shipment.Id shipmentId = Shipment.Id.derivedFrom(referenceId);
    private final String streamId = ShipmentFacade.mapToStreamId(shipmentId);
    private final Shipment.ProductItem[] productItems =
            new Shipment.ProductItem[] {new Shipment.ProductItem(UUID.randomUUID(), 2)};
    private final OffsetDateTime now = OffsetDateTime.now();

    private final ShipmentSummaries summaries;

    ShipmentSummariesTests(ProjectionsDatabase database) {
        summaries = new ShipmentSummaries(database.bean(ShipmentSummaryRepository.class));
    }

    @Test
    public void reservedStockIsSummarizedWithTheRequester() {
        summaries.handle(at(0, new StockReserved(shipmentId, referenceId, productItems, now)));

        var summary = summaries.get(shipmentId).orElseThrow();

        assertThat(summary.getReferenceId()).isEqualTo(referenceId);
        assertThat(summary.getVersion()).isEqualTo(0);
    }

    @Test
    public void productsOutOfStockAreSummarizedWithTheRequester() {
        summaries.handle(at(0, new ProductWasOutOfStock(shipmentId, referenceId, productItems, now)));

        assertThat(summaries.get(shipmentId).orElseThrow().getReferenceId()).isEqualTo(referenceId);
    }

    @Test
    public void onlyTheEventsThatRecordTheRequestAreSummarized() {
        assertThat(summaries.canHandle()).containsExactly(StockReserved.class, ProductWasOutOfStock.class);
    }

    @Test
    public void summaryOfAShipmentThatWasNeverRequestedIsNotThere() {
        assertThat(summaries.get(shipmentId)).isEmpty();
    }

    private <Event> MessageEnvelope<Event> at(long streamPosition, Event event) {
        return new MessageEnvelope<>(event, Metadata.at(streamId, streamPosition));
    }
}
