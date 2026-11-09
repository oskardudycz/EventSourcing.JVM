package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import org.jetbrains.annotations.NotNull;
import org.springframework.amqp.rabbit.support.micrometer.RabbitMessageReceiverContext;
import org.springframework.stereotype.Component;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.messages.MessageMetadata.CAUSATION_ID;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.messages.MessageMetadata.MESSAGE_ID;

@Component
public class RabbitMessageBaggageHandler implements ObservationHandler<RabbitMessageReceiverContext> {

  private static final String CAUSATION_SCOPE_KEY = "rabbit.baggage.causation";
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
    Object messageId = context.getCarrier().getMessageProperties().getHeader(MESSAGE_ID);
    if (messageId == null)
      return;

    context.put(CAUSATION_SCOPE_KEY, tracer.createBaggageInScope(CAUSATION_ID, messageId.toString()));
  }

  @Override
  public void onScopeClosed(RabbitMessageReceiverContext context) {
    BaggageInScope causation = context.get(CAUSATION_SCOPE_KEY);
    if (causation != null)
      causation.close();
  }
}
