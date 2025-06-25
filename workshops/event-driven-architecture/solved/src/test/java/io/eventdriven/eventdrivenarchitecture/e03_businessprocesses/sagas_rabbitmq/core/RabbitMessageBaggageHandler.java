package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jetbrains.annotations.NotNull;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.support.micrometer.RabbitMessageReceiverContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.ENTITY_ID;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.ENTITY_VERSION;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.getEntityIdFromBaggage;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.getEntityVersionFromBaggage;

@Component
public class RabbitMessageBaggageHandler implements ObservationHandler<RabbitMessageReceiverContext> {

  private static final String BAGGAGE_SCOPES_KEY = "rabbit.baggage.scopes";
  private final Tracer tracer;

  public RabbitMessageBaggageHandler(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public boolean supportsContext(Observation.@NotNull Context context) {
    return context instanceof RabbitMessageReceiverContext;
  }

  @Override
  public void onScopeOpened(RabbitMessageReceiverContext context) {
    var headers = context.getCarrier().getMessageProperties().getHeaders();
    List<BaggageInScope> scopes = new ArrayList<>();

    addBaggageIfPresent(scopes, headers, MESSAGE_ID, CAUSATION_ID);
    addBaggageIfPresent(scopes, headers, CAUSATION_ID);
    addBaggageIfPresent(scopes, headers, ENTITY_ID);
    addBaggageIfPresent(scopes, headers, ENTITY_VERSION);

    if (!scopes.isEmpty()) {
      context.put(BAGGAGE_SCOPES_KEY, scopes);
    }
  }

  @Override
  public void onScopeClosed(RabbitMessageReceiverContext context) {
    List<BaggageInScope> scopes = context.get(BAGGAGE_SCOPES_KEY);
    if (scopes != null) {
      scopes.forEach(BaggageInScope::close);
    }
  }

  private void addBaggageIfPresent(List<BaggageInScope> scopes, Map<String, Object> headers, String from, String to) {
    var value = headers.get(from);
    if (value == null) return;

    String stringValue;
    if (value instanceof byte[]) {
      stringValue = new String((byte[]) value);
    } else {
      stringValue = value.toString();
    }

    scopes.add(tracer.createBaggageInScope(to, stringValue));
  }

  private void addBaggageIfPresent(List<BaggageInScope> scopes, Map<String, Object> headers, String key) {
    addBaggageIfPresent(scopes, headers, key, key);
  }

  public static void setHeadersFromBaggage(Message message, Tracer tracer) {
    var properties = message.getMessageProperties();

    var correlationId = getCorrelationIdFromBaggage(tracer)
      .orElse(java.util.UUID.randomUUID().toString());
    properties.setHeader(CORRELATION_ID, correlationId);

    getEntityIdFromBaggage(tracer).ifPresent(value ->
      properties.setHeader(CAUSATION_ID, value));

    var messageId = java.util.UUID.randomUUID().toString();
    properties.setHeader("messageId", messageId);

    getEntityIdFromBaggage(tracer).ifPresent(value ->
      properties.setHeader(ENTITY_ID, value));
    getEntityVersionFromBaggage(tracer).ifPresent(value ->
      properties.setHeader(ENTITY_VERSION, value));
  }
}
