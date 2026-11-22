package io.eventdriven.core.documentation;

import io.eventdriven.core.messaging.MessageBus;
import io.github.springwolf.core.controller.PublishingBaseController;
import io.github.springwolf.core.controller.PublishingPayloadCreator;
import io.github.springwolf.core.controller.dtos.MessageDto;
import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${springwolf.path.base:/springwolf}/plugin/" + InMemoryBindings.protocol)
public class InMemoryPublishingController extends PublishingBaseController {
    private final MessageBus messageBus;

    public InMemoryPublishingController(PublishingPayloadCreator publishingPayloadCreator, MessageBus messageBus) {
        super(publishingPayloadCreator);
        this.messageBus = messageBus;
    }

    @Override
    protected boolean isEnabled() {
        return true;
    }

    @Override
    protected void publishMessage(String topic, MessageDto message, Object payload) {
        messageBus.publish(List.of(payload));
    }
}
