package io.eventdriven.introductiontoeventsourcing.e04_getting_state_from_events.kurrentdb.tools;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.eventdriven.eventstores.testing.tools.TestDatabases;
import io.kurrent.dbclient.*;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public abstract class KurrentDBTest {
    protected KurrentDBClient eventStore;

    protected static final ObjectMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();
    ;

    @BeforeEach
    void beforeEach() throws ConnectionStringParsingException {
        KurrentDBClientSettings settings =
                KurrentDBConnectionString.parseOrThrow(TestDatabases.kurrentDBConnectionString());
        eventStore = KurrentDBClient.create(settings);
    }

    protected CompletableFuture<WriteResult> appendEvents(
            KurrentDBClient eventStore, String streamName, Object[] events) {
        // 1. Add logic here
        return eventStore.appendToStream(
                streamName, Arrays.stream(events).map(this::serialize).iterator());
    }

    private EventData serialize(Object event) {
        try {
            return EventDataBuilder.json(
                            UUID.randomUUID(), event.getClass().getTypeName(), mapper.writeValueAsBytes(event))
                    .build();
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }
}
