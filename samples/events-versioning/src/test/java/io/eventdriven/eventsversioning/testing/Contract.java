package io.eventdriven.eventsversioning.testing;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class Contract {
    private static final ObjectMapper DEFAULT_MAPPER =
        new JsonMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private final ObjectMapper mapper;

    private Contract(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static Contract specification() {
        return new Contract(DEFAULT_MAPPER);
    }

    public static Contract specification(ObjectMapper mapper) {
        return new Contract(mapper);
    }

    public <S> GivenStep<S> given(Snapshot.ByClass<S> snapshot) {
        return new GivenStep<>(snapshot, null, mapper);
    }

    public GivenStep<Object> given(Snapshot.ByMessageType snapshot) {
        return new GivenStep<>(snapshot, null, mapper);
    }

    public GivenStep<Object> given(Snapshot.ByPath snapshot) {
        return new GivenStep<>(snapshot, null, mapper);
    }

    public <S> GivenStep<S> given(S instance) {
        return new GivenStep<>(null, instance, mapper);
    }
}
