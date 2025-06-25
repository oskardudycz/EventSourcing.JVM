package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.jetbrains.annotations.NotNull;
import org.springframework.kafka.support.micrometer.KafkaRecordReceiverContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.ENTITY_ID;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.ENTITY_VERSION;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.getEntityIdFromBaggage;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.getEntityVersionFromBaggage;

@Component
public class KafkaMessageBaggageHandler implements ObservationHandler<KafkaRecordReceiverContext> {

  private static final String BAGGAGE_SCOPES_KEY = "kafka.baggage.scopes";
  private final Tracer tracer;

  public KafkaMessageBaggageHandler(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public boolean supportsContext(Observation.@NotNull Context context) {
    return context instanceof KafkaRecordReceiverContext;
  }

  @Override
  public void onScopeOpened(KafkaRecordReceiverContext context) {
    var headers = context.getRecord().headers();
    List<BaggageInScope> scopes = new ArrayList<>();

    addBaggageIfPresent(scopes, headers, MESSAGE_ID, CAUSATION_ID);
    addBaggageIfPresent(scopes, headers, CORRELATION_ID);
    addBaggageIfPresent(scopes, headers, ENTITY_ID);
    addBaggageIfPresent(scopes, headers, ENTITY_VERSION);

    if (!scopes.isEmpty()) {
      context.put(BAGGAGE_SCOPES_KEY, scopes);
    }
  }

  @Override
  public void onScopeClosed(KafkaRecordReceiverContext context) {
    List<BaggageInScope> scopes = context.get(BAGGAGE_SCOPES_KEY);
    if (scopes != null) {
      scopes.forEach(BaggageInScope::close);
    }
  }

  private void addBaggageIfPresent(List<BaggageInScope> scopes, Headers headers, String from, String to) {
    var header = headers.lastHeader(from);
    if (header == null)
      return;

    String value = new String(header.value());
    scopes.add(tracer.createBaggageInScope(to, value));
  }

  private void addBaggageIfPresent(List<BaggageInScope> scopes, Headers headers, String key) {
    addBaggageIfPresent(scopes, headers, key, key);
  }

  public static void setHeadersFromBaggage(ProducerRecord<String, Object> record, Tracer tracer) {
    var correlationId = getCorrelationIdFromBaggage(tracer)
      .orElse(java.util.UUID.randomUUID().toString());
    record.headers().add(CORRELATION_ID, correlationId.getBytes());

    getEntityIdFromBaggage(tracer).ifPresent(value ->
      record.headers().add(CAUSATION_ID, value.getBytes()));

    var messageId = java.util.UUID.randomUUID().toString();
    record.headers().add(MESSAGE_ID, messageId.getBytes());

    getEntityIdFromBaggage(tracer).ifPresent(value ->
      record.headers().add(ENTITY_ID, value.getBytes()));
    getEntityVersionFromBaggage(tracer).ifPresent(value ->
      record.headers().add(ENTITY_VERSION, value.toString().getBytes()));
  }
}
