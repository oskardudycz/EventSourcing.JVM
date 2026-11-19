package io.eventdriven.uniqueness.shoppingcarts;

import static io.eventdriven.uniqueness.shoppingcarts.ShoppingCartEvent.ShoppingCartOpened;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.uniqueness.core.serialization.EventSerializer;
import io.eventdriven.uniqueness.testing.TestDatabases;
import io.kurrent.dbclient.*;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ShoppingCartTests {

    @Test
    public void enforcesUniqueness_WithStreamId() throws ExecutionException, InterruptedException {
        var clientId = UUID.randomUUID();
        // We're assuming that there can be only a single shopping cart open for specific client.
        // We can enforce uniqueness by putting client id into a stream id
        var shoppingCartStreamId = "shopping_cart-%s".formatted(clientId);
        var shoppingCartOpened = new ShoppingCartOpened(clientId, clientId);

        // This one should succeed as we don't have such stream yet
        eventStore
                .appendToStream(
                        shoppingCartStreamId,
                        AppendToStreamOptions.get().streamState(StreamState.noStream()),
                        EventSerializer.serialize(shoppingCartOpened))
                .get();

        // This one will fail, as we're expecting that stream doesn't exist
        try {
            eventStore
                    .appendToStream(
                            shoppingCartStreamId,
                            AppendToStreamOptions.get().streamState(StreamState.noStream()),
                            EventSerializer.serialize(shoppingCartOpened))
                    .get();
        } catch (ExecutionException exception) {
            assertThat(exception.getCause()).isInstanceOf(WrongExpectedVersionException.class);
        }
    }

    private KurrentDBClient eventStore;

    @BeforeEach
    void beforeEach() throws ConnectionStringParsingException {
        KurrentDBClientSettings settings =
                KurrentDBConnectionString.parseOrThrow(TestDatabases.kurrentDBConnectionString());
        this.eventStore = KurrentDBClient.create(settings);
    }
}
