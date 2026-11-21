package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.messaging.KafkaMessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageChannel;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import io.eventdriven.testing.KafkaTopics;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestKafka;
import io.eventdriven.testing.TestMessages;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class PaymentExternalEventKafkaRedeliveryTests extends PaymentExternalEventBrokerRedeliveryTests {
    private final String bootstrapServers = TestDatabases.kafkaBootstrapServers();

    @Autowired
    private ObservationRegistry observations;

    private KafkaMessageBus messageBus;

    @Override
    protected MessageBus openBus(List<MessageRoutes> routes, List<MessageChannel> channels) {
        messageBus = KafkaMessageBus.connectingTo(
                bootstrapServers,
                messageContext,
                observations,
                TestMessages.serializer(),
                TestKafka.PUBLISH_TIMEOUT,
                TestKafka.failureHandling(bootstrapServers, observations),
                routes,
                channels,
                List.of());

        return messageBus;
    }

    @Override
    protected List<String> awaitPublishedMessageIds(int count) {
        await().atMost(Duration.ofSeconds(30))
                .until(() -> KafkaTopics.recordsOf(bootstrapServers, responses).size() >= count);

        return KafkaTopics.recordsOf(bootstrapServers, responses).stream()
                .map(record -> KafkaTopics.header(record, KafkaMessageBus.MESSAGE_ID))
                .toList();
    }

    @Override
    protected void closeBusAndDeleteChannels() {
        messageBus.close();
        KafkaTopics.deleteContaining(bootstrapServers, responses);
    }
}
