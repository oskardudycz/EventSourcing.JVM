package io.eventdriven.testing;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.SendResult;

public class FailingFirstSendKafkaTemplate extends KafkaTemplate<String, byte[]> {
    private final AtomicBoolean failed = new AtomicBoolean();

    public FailingFirstSendKafkaTemplate(ProducerFactory<String, byte[]> producerFactory) {
        super(producerFactory);
    }

    @Override
    public CompletableFuture<SendResult<String, byte[]>> send(ProducerRecord<String, byte[]> record) {
        if (failed.compareAndSet(false, true))
            return CompletableFuture.failedFuture(new KafkaException("The broker refused the first send"));

        return super.send(record);
    }
}
