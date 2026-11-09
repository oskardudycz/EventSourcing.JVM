package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import org.jetbrains.annotations.NotNull;
import org.springframework.kafka.support.micrometer.KafkaRecordReceiverContext;
import org.springframework.stereotype.Component;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.CAUSATION_ID;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.MESSAGE_ID;

@Component
public class KafkaMessageBaggageHandler implements ObservationHandler<KafkaRecordReceiverContext> {

  private static final String CAUSATION_SCOPE_KEY = "kafka.baggage.causation";
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
    var messageId = context.getRecord().headers().lastHeader(MESSAGE_ID);
    if (messageId == null)
      return;

    context.put(CAUSATION_SCOPE_KEY, tracer.createBaggageInScope(CAUSATION_ID, new String(messageId.value())));
  }

  @Override
  public void onScopeClosed(KafkaRecordReceiverContext context) {
    BaggageInScope causation = context.get(CAUSATION_SCOPE_KEY);
    if (causation != null)
      causation.close();
  }
}
