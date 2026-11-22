package io.eventdriven.ecommerce.orders;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.core.eventstore.Subscription;
import io.eventdriven.core.messaging.EventEnvelope;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("in-memory")
@Import(OrderSagaSubscriptionOrderTests.RecordingSubscriptions.class)
public class OrderSagaSubscriptionOrderTests {
    private static final List<String> subscriptionNames = new CopyOnWriteArrayList<>();

    @Test
    public void theOrderSagaSubscribesToTheEventStoreAfterEveryExternalEventForwarder() {
        assertThat(subscriptionNames)
                .contains(
                        "shopping-carts-external-forwarder",
                        "payments-external-forwarder",
                        "shipments-external-forwarder")
                .endsWith("order-saga");
    }

    @TestConfiguration
    static class RecordingSubscriptions {
        @Bean
        static BeanPostProcessor recordingSubscriptions() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    return bean instanceof EventStore eventStore ? new RecordingEventStore(eventStore) : bean;
                }
            };
        }
    }

    static class RecordingEventStore implements EventStore {
        private final EventStore store;

        RecordingEventStore(EventStore store) {
            this.store = store;
        }

        @Override
        public ReadResult read(String streamId) {
            return store.read(streamId);
        }

        @Override
        public AppendResult append(String streamId, Object... events) {
            return store.append(streamId, events);
        }

        @Override
        public AppendResult append(String streamId, ExpectedStreamVersion expectedVersion, Object... events) {
            return store.append(streamId, expectedVersion, events);
        }

        @Override
        public Subscription subscribe(String name, List<Class<?>> types, Consumer<EventEnvelope<Object>> handler) {
            subscriptionNames.add(name);

            return store.subscribe(name, types, handler);
        }
    }
}
