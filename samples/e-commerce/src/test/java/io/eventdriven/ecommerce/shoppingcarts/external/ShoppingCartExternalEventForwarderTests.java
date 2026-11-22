package io.eventdriven.ecommerce.shoppingcarts.external;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.testing.projections.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.messaging.MessageCatcher;
import io.eventdriven.testing.messaging.Metadata;
import io.eventdriven.testing.tracing.TestTracing;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ShoppingCartExternalEventForwarderTests {
    private final UUID customerId = UUID.randomUUID();
    private final ShoppingCart.Id shoppingCartId = ShoppingCart.Id.of(customerId, 1);
    private final PricedProductItem pricedShoes = new PricedProductItem(new ProductItem(UUID.randomUUID(), 2), 12.5);
    private final OffsetDateTime now = OffsetDateTime.now();

    @Test
    public void forwarderRefusesASummaryThatHasNotSeenTheEventSoTheStoreDeliversItAgain() {
        var summaries = new ShoppingCartSummaries(H2.bean(ShoppingCartSummaryRepository.class));
        var bus = new InMemoryMessageBus(
                TestTracing.untraced(),
                List.of(new MessageRoutes()
                        .routeTarget(MessageRoutes.inPackage("io.eventdriven.ecommerce"), "published")),
                List.of(new MessageChannel("published")),
                List.of());
        var published = new MessageCatcher();
        bus.use(envelope -> published.catchMessage(envelope.message()));
        var forwarder = new ShoppingCartExternalEventForwarder(summaries, bus);
        var streamId = ShoppingCartFacade.mapToStreamId(shoppingCartId);

        summaries.handle(new MessageEnvelope<>(
                new ShoppingCartEvent.ProductItemAddedToShoppingCart(shoppingCartId, customerId, pricedShoes, now),
                Metadata.at(streamId, 0)));
        MessageEnvelope<Object> checkedOut = new MessageEnvelope<>(
                new ShoppingCartEvent.ShoppingCartCheckedOut(shoppingCartId, now), Metadata.at(streamId, 1));

        assertThatThrownBy(() -> forwarder.handle(checkedOut)).isInstanceOf(IllegalStateException.class);
        published.shouldNotReceiveAnyEvent();

        summaries.handle(checkedOut);
        forwarder.handle(checkedOut);

        published.shouldReceiveMessages(new ShoppingCartExternalEvent.ShoppingCartCheckedOut(
                shoppingCartId, customerId, new PricedProductItem[] {pricedShoes}, 25, now));
    }
}
