package io.eventdriven.core.documentation;

import io.github.springwolf.asyncapi.v3.model.AsyncAPI;
import io.github.springwolf.asyncapi.v3.model.channel.message.MessageObject;
import io.github.springwolf.core.asyncapi.AsyncApiCustomizer;

// Springwolf copies a payload's description to its message only for inline payloads. Records are
// referenced, so their @Schema description is copied here.
public class MessageDescriptions implements AsyncApiCustomizer {
    @Override
    public void customize(AsyncAPI asyncApi) {
        var schemas = asyncApi.getComponents().getSchemas();

        for (var message : asyncApi.getComponents().getMessages().values()) {
            if (!(message instanceof MessageObject messageObject) || messageObject.getDescription() != null) continue;

            var schema = schemas.get(messageObject.getName());
            if (schema != null && schema.getSchema() != null)
                messageObject.setDescription(schema.getSchema().getDescription());
        }
    }
}
