package io.eventdriven.testing.contracts;

import io.eventdriven.strictland.MessageSerializer;
import tools.jackson.databind.json.JsonMapper;

record JsonMapperSerializer(JsonMapper mapper) implements MessageSerializer {
    @Override
    public String fileExtension() {
        return "json";
    }

    @Override
    public byte[] serialize(Object value) {
        return mapper.writeValueAsBytes(value);
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> type) {
        return mapper.readValue(bytes, type);
    }
}
