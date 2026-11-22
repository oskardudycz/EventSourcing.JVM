package io.eventdriven.testing.messaging.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

public final class KafkaTopics {
    private KafkaTopics() {}

    public static List<ConsumerRecord<String, byte[]>> recordsOf(String bootstrapServers, String topic) {
        try (var consumer = new KafkaConsumer<>(
                Map.<String, Object>of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        bootstrapServers,
                        ConsumerConfig.GROUP_ID_CONFIG,
                        "reader-" + UUID.randomUUID(),
                        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                        false),
                new StringDeserializer(),
                new ByteArrayDeserializer())) {
            var partitions = consumer.partitionsFor(topic).stream()
                    .map(partition -> new TopicPartition(topic, partition.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            var ends = consumer.endOffsets(partitions);

            var records = new ArrayList<ConsumerRecord<String, byte[]>>();
            while (partitions.stream().anyMatch(partition -> consumer.position(partition) < ends.get(partition))) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            return records;
        }
    }

    public static List<String> namesContaining(String bootstrapServers, String text) {
        try (var admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", bootstrapServers))) {
            return admin.listTopics().names().get().stream()
                    .filter(name -> name.contains(text))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void deleteContaining(String bootstrapServers, String text) {
        try (var admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", bootstrapServers))) {
            var names = admin.listTopics().names().get().stream()
                    .filter(name -> name.contains(text))
                    .toList();
            for (var deletion : admin.deleteTopics(names).topicNameValues().values()) {
                try {
                    deletion.get();
                } catch (ExecutionException e) {
                    if (!(e.getCause() instanceof UnknownTopicOrPartitionException)) throw e;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String header(ConsumerRecord<String, byte[]> record, String name) {
        Header header = record.headers().lastHeader(name);

        return header != null ? new String(header.value()) : null;
    }
}
