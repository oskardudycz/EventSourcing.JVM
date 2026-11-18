package io.eventdriven.distributedprocesses.ecommerce;

import io.github.springwolf.asyncapi.v3.model.AsyncAPI;
import io.github.springwolf.asyncapi.v3.model.channel.ChannelObject;
import io.github.springwolf.asyncapi.v3.model.operation.Operation;
import io.github.springwolf.core.asyncapi.grouping.GroupingService;
import io.github.springwolf.core.configuration.docket.AsyncApiGroup;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// A module's document describes what its code does: the operations it sends or receives, and the
// channels they use, also the ones another module owns.
@Component
public class ModuleGroupingService extends GroupingService {
    @Override
    public AsyncAPI groupAPI(AsyncAPI fullAsyncApi, AsyncApiGroup group) {
        Map<String, Operation> operations = fullAsyncApi.getOperations().entrySet().stream()
                .filter(operation -> group.getGroupName()
                        .equals(operation.getValue().getExtensionFields().get(ModuleOperations.module)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        var usedChannels = operations.values().stream()
                .map(operation -> operation.getChannel().getRef().substring("#/channels/".length()))
                .collect(Collectors.toSet());

        Map<String, ChannelObject> channels = fullAsyncApi.getChannels().entrySet().stream()
                .filter(channel -> usedChannels.contains(channel.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        var moduleAsyncApi = AsyncAPI.builder()
                .asyncapi(fullAsyncApi.getAsyncapi())
                .id(fullAsyncApi.getId())
                .info(fullAsyncApi.getInfo())
                .defaultContentType(fullAsyncApi.getDefaultContentType())
                .servers(fullAsyncApi.getServers())
                .channels(channels)
                .operations(operations)
                .components(fullAsyncApi.getComponents())
                .build();

        return super.groupAPI(moduleAsyncApi, group);
    }
}
