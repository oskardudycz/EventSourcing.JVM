package io.eventdriven.core.documentation;

import io.github.springwolf.asyncapi.v3.bindings.EmptyMessageBinding;
import io.github.springwolf.asyncapi.v3.bindings.EmptyOperationBinding;
import io.github.springwolf.asyncapi.v3.model.AsyncAPI;
import io.github.springwolf.asyncapi.v3.model.channel.message.MessageObject;
import io.github.springwolf.core.asyncapi.AsyncApiCustomizer;
import java.util.Map;

// AsyncAPI allows only its known protocols as binding names, and others only with the x- prefix.
// Springwolf's UI reads the protocol from the binding name, so it needs one on every operation and message.
public class InMemoryBindings implements AsyncApiCustomizer {
    public static final String protocol = "x-in-memory";

    @Override
    public void customize(AsyncAPI asyncApi) {
        for (var operation : asyncApi.getOperations().values())
            operation.setBindings(Map.of(protocol, new EmptyOperationBinding()));

        for (var message : asyncApi.getComponents().getMessages().values())
            if (message instanceof MessageObject messageObject)
                messageObject.setBindings(Map.of(protocol, new EmptyMessageBinding()));
    }
}
