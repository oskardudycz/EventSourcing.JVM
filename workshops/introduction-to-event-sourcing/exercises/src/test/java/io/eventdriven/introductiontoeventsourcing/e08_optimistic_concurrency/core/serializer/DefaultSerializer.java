package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.core.serializer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public final class DefaultSerializer {
    public static JsonMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();
}
