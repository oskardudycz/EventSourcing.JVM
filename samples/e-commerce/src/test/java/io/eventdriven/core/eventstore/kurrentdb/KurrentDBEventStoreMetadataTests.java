package io.eventdriven.core.eventstore.kurrentdb;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.EventStoreMetadataTests;
import io.eventdriven.core.processing.RetryBackOff;
import io.eventdriven.core.subscriptions.SubscriptionCheckpoints;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.projections.ProjectionsDatabase;
import io.kurrent.dbclient.EventDataBuilder;
import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.KurrentDBConnectionString;
import io.kurrent.dbclient.ReadStreamOptions;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class KurrentDBEventStoreMetadataTests extends EventStoreMetadataTests {
    private final KurrentDBClient client =
            KurrentDBClient.create(KurrentDBConnectionString.parseOrThrow(TestDatabases.kurrentDBConnectionString()));
    private final KurrentDBEventStore eventStore = new KurrentDBEventStore(
            client,
            context,
            serializer(),
            ObservationRegistry.NOOP,
            ProjectionsDatabase.H2.bean(SubscriptionCheckpoints.class),
            RetryBackOff.exponential(Duration.ofMillis(100)));

    @Override
    protected EventStore eventStore() {
        return eventStore;
    }

    @AfterEach
    void closeStore() {
        eventStore.close();
    }

    @Test
    public void kurrentDBMetadataUsesItsDollarNames() throws Exception {
        var testSpan = tracing.tracer().nextSpan().name("test").start();

        try (var ignored = tracing.tracer().withSpan(testSpan);
                var scope = context.handling("m-1", "c-1")) {
            eventStore.append(streamId, new Opened("cart"));
        } finally {
            testSpan.end();
        }

        var raw = client.readStream(streamId, ReadStreamOptions.get())
                .get()
                .getEvents()
                .getFirst()
                .getEvent()
                .getUserMetadata();
        var keys = new JsonMapper().readTree(raw).propertyNames().stream().collect(Collectors.toSet());

        assertThat(keys).contains("$correlationId", "$causationId", "$traceId", "$spanId");
    }

    @Test
    public void messageIdIsTheKurrentDBEventId() throws Exception {
        eventStore.append(streamId, new Opened("cart"));

        var rawEventId = client.readStream(streamId, ReadStreamOptions.get())
                .get()
                .getEvents()
                .getFirst()
                .getEvent()
                .getEventId();

        assertThat(readMetadata(streamId).getFirst().messageId()).isEqualTo(rawEventId.toString());
    }

    @Test
    public void eventWithoutMetadataGetsItsOwnIdAsCorrelationAndCausation() throws Exception {
        appendRaw("{\"cartId\":\"cart\"}", null);

        var metadata = readMetadata(streamId).getFirst();

        assertThat(metadata.correlationId()).isEqualTo(metadata.messageId());
        assertThat(metadata.causationId()).isEqualTo(metadata.messageId());
        assertThat(metadata.traceId()).isNull();
        assertThat(metadata.spanId()).isNull();
    }

    @Test
    public void metadataValuesThatAreNotTextAreReadAsTextOrIgnored() throws Exception {
        appendRaw(
                "{\"cartId\":\"cart\"}",
                "{\"$correlationId\":\"c-1\",\"$causationId\":5,\"$traceId\":{\"nested\":true}}");

        var metadata = readMetadata(streamId).getFirst();

        assertThat(metadata.correlationId()).isEqualTo("c-1");
        assertThat(metadata.causationId()).isEqualTo("5");
        assertThat(metadata.traceId()).isNull();
    }

    @Test
    public void metadataThatIsNotAJsonObjectIsTreatedAsNoMetadata() throws Exception {
        appendRaw("{\"cartId\":\"cart\"}", "[1,2]");

        var metadata = readMetadata(streamId).getFirst();

        assertThat(metadata.correlationId()).isEqualTo(metadata.messageId());
        assertThat(metadata.traceId()).isNull();
    }

    @Test
    public void jsonNullMetadataIsTreatedAsNoMetadata() throws Exception {
        appendRaw("{\"cartId\":\"cart\"}", "null");

        var metadata = readMetadata(streamId).getFirst();

        assertThat(metadata.correlationId()).isEqualTo(metadata.messageId());
    }

    private void appendRaw(String data, String metadata) throws Exception {
        var event = EventDataBuilder.json(Opened.class.getName(), data.getBytes());
        if (metadata != null) event.metadataAsBytes(metadata.getBytes());

        client.appendToStream(streamId, event.build()).get();
    }
}
