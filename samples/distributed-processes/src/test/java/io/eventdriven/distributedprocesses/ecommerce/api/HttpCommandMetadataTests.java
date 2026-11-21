package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.testing.ApiSpecification;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("in-memory")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestConfigurations.ChangeablePrices.class)
public class HttpCommandMetadataTests extends ApiSpecification {
    @Autowired
    private EventStore eventStore;

    @Autowired
    private TestConfigurations.Prices prices;

    public HttpCommandMetadataTests() {
        super("customers");
    }

    @Test
    public void aStepWithNothingToInheritGeneratesTheCorrelationId() {
        var first = correlationIdsOfAPurchase();
        var second = correlationIdsOfAPurchase();

        assertThat(first.correlationIds())
                .isNotEmpty()
                .doesNotContainNull()
                .allSatisfy(id -> assertThat(id).isNotBlank());
        assertThat(first.causationIds()).isEqualTo(first.correlationIds());
        assertThat(second.correlationIds().getFirst())
                .isNotEqualTo(first.correlationIds().getFirst());
    }

    private record Ids(List<String> correlationIds, List<String> causationIds) {}

    private Ids correlationIdsOfAPurchase() {
        var customerId = UUID.randomUUID();
        var shoes = UUID.randomUUID();
        prices.set(shoes, 50);

        PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(customerId).withProduct(shoes, 2));

        var streamId = ShoppingCartFacade.mapToStreamId(ShoppingCartId.of(customerId, 1));
        var metadata = ((EventStore.ReadResult.Success) eventStore.read(streamId))
                .events().stream().map(EventEnvelope::metadata).toList();

        return new Ids(
                metadata.stream().map(EventMetadata::correlationId).toList(),
                metadata.stream().map(EventMetadata::causationId).toList());
    }
}
