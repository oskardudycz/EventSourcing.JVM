package io.eventdriven.core.documentation;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.springwolf.core.asyncapi.components.ComponentsService;
import io.github.springwolf.core.controller.PublishingPayloadCreator;
import io.github.springwolf.core.controller.dtos.MessageDto;
import java.util.Optional;
import org.springframework.util.ClassUtils;

// Springwolf loads a payload class with Class.forName, which needs Outer$Inner. Our messages are records
// nested in their sealed interface, and their schema names are Outer.Inner, which ClassUtils resolves.
public class NestedRecordPayloadCreator extends PublishingPayloadCreator {
    private final ComponentsService componentsService;
    private final ObjectMapper jsonMapper;

    public NestedRecordPayloadCreator(ComponentsService componentsService, ObjectMapper jsonMapper) {
        super(componentsService, jsonMapper);
        this.componentsService = componentsService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Result createPayloadObject(MessageDto message) {
        if (!componentsService.getSchemas().containsKey(message.getType())) return super.createPayloadObject(message);

        try {
            var payloadClass = ClassUtils.forName(message.getType(), getClass().getClassLoader());
            return new Result(jsonMapper.readValue(message.getPayload(), payloadClass), Optional.empty());
        } catch (ClassNotFoundException | JacksonException exception) {
            return new Result(
                    null,
                    Optional.of(
                            "Unable to create payload %s: %s".formatted(message.getType(), exception.getMessage())));
        }
    }
}
