package io.eventdriven.core.documentation;

import io.github.springwolf.asyncapi.v3.model.AsyncAPI;
import io.github.springwolf.asyncapi.v3.model.channel.message.MessageObject;
import io.github.springwolf.core.asyncapi.AsyncApiCustomizer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// AsyncAPI has only messages. EventCatalog reads this extension to show which are commands.
public class EventCatalogMessageTypes implements AsyncApiCustomizer {
    private static final String messageType = "x-eventcatalog-message-type";

    private final Set<String> commands;

    public EventCatalogMessageTypes(List<Class<?>> commandTypes) {
        this.commands = commandTypes.stream()
                .flatMap(command -> Arrays.stream(command.getPermittedSubclasses()))
                .map(Class::getCanonicalName)
                .collect(Collectors.toSet());
    }

    @Override
    public void customize(AsyncAPI asyncApi) {
        for (var message : asyncApi.getComponents().getMessages().values()) {
            if (!(message instanceof MessageObject messageObject)) continue;

            var extensions = messageObject.getExtensionFields() != null
                    ? new HashMap<>(messageObject.getExtensionFields())
                    : new HashMap<String, Object>();
            extensions.put(messageType, commands.contains(messageObject.getName()) ? "command" : "event");
            messageObject.setExtensionFields(extensions);
        }
    }
}
