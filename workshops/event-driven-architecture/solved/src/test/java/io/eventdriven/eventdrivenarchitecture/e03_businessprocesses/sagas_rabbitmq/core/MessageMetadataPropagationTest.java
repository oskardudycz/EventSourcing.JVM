package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.ITestableMessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.TestApplicationConfig;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts.GroupCheckoutEvent;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts.GroupCheckoutFacade;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade.GuestStayAccountCommand.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = TestApplicationConfig.class)
@ComponentScan(basePackages = {
  "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core",
  "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core",
  "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates"
})
@TestPropertySource(properties = "messaging.type=rabbitmq")
@Import(RabbitMQTestConfiguration.class)
@AutoConfigureObservability
public class MessageMetadataPropagationTest {
  @TestConfiguration(proxyBeanMethods = false)
  static class TracingTestConfig {
    private static final InMemorySpanExporter spanExporter = InMemorySpanExporter.create();

    @Bean
    InMemorySpanExporter inMemorySpanExporter() {
      return spanExporter;
    }

    @Bean
    @Primary
    OpenTelemetry openTelemetry() {
      SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
        .build();

      return OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .build();
    }

    @Bean
    @Primary
    Tracer testTracer(OpenTelemetry openTelemetry) {
      OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();

      OtelBaggageManager otelBaggageManager = new OtelBaggageManager(
        otelCurrentTraceContext,
        Arrays.asList("messageId", "correlationId", "causationId", "entityId", "entityVersion"),
        Collections.emptyList()
      );

      return new OtelTracer(
        openTelemetry.getTracer("test-tracer"),
        otelCurrentTraceContext,
        event -> {
        },
        otelBaggageManager
      );
    }
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("app.rabbitmq.exchange", () -> "hotel-financial-metadata-test");
    registry.add("test.context.id", () -> "metadata-test");
  }

  @Autowired
  private Database database;
  @Autowired
  private ITestableMessageBus messageBus;
  @Autowired
  private Tracer tracer;
  @Autowired
  private GuestStayAccountFacade guestStayFacade;
  @Autowired
  private GroupCheckoutFacade groupCheckoutFacade;
  @Autowired
  private InMemorySpanExporter spanExporter;
  private MessageCatcher publishedMessages;
  private OffsetDateTime now;

  @BeforeEach
  public void setUp() {
    publishedMessages = new MessageCatcher();
    now = OffsetDateTime.now();
    messageBus.clearMiddleware();
    messageBus.use(publishedMessages::catchMessage);
    spanExporter.reset();
  }

  @Disabled("TODO: Check propagation")
  @Test
  public void shouldPropagateMessageMetadataCorrectly() {
    AtomicReference<String> initiatedMessageId = new AtomicReference<>();
    AtomicReference<String> initiatedCorrelationId = new AtomicReference<>();
    AtomicReference<String> initiatedTraceId = new AtomicReference<>();
    AtomicReference<String> initiatedSpanId = new AtomicReference<>();

    AtomicReference<String> checkedOutMessageId = new AtomicReference<>();
    AtomicReference<String> checkedOutCorrelationId = new AtomicReference<>();
    AtomicReference<String> checkedOutCausationId = new AtomicReference<>();
    AtomicReference<String> checkedOutTraceId = new AtomicReference<>();
    AtomicReference<String> checkedOutSpanId = new AtomicReference<>();

    messageBus.subscribe(GroupCheckoutEvent.GroupCheckoutInitiated.class, event -> {
      if (initiatedMessageId.get() != null) return;

      var correlationId = tracer.getBaggage("correlationId");
      var causationId = tracer.getBaggage("causationId");
      var currentSpan = tracer.currentSpan();

      assertThat(causationId).as("causationId should be present").isNotNull();
      assertThat(causationId.get()).as("causationId should not be empty").isNotNull().isNotEmpty();
      initiatedMessageId.set(causationId.get());

      assertThat(correlationId).as("correlationId should be present").isNotNull();
      assertThat(correlationId.get()).as("correlationId should not be empty").isNotNull().isNotEmpty();
      initiatedCorrelationId.set(correlationId.get());

      assertThat(causationId).as("Initial event causationId should not be null").isNotNull();

      assertThat(currentSpan).as("Current span should be present in handler").isNotNull();
      assertThat(currentSpan.context().traceId()).as("TraceId should be present").isNotEmpty();
      assertThat(currentSpan.context().spanId()).as("SpanId should be present").isNotEmpty();
      initiatedTraceId.set(currentSpan.context().traceId());
      initiatedSpanId.set(currentSpan.context().spanId());
    });

    messageBus.subscribe(GuestStayAccountEvent.GuestCheckedOut.class, event -> {
      if (checkedOutMessageId.get() != null) return;

      var correlationId = tracer.getBaggage("correlationId");
      var causationId = tracer.getBaggage("causationId");
      var currentSpan = tracer.currentSpan();

      assertThat(causationId).as("messageId should be present").isNotNull();
      assertThat(causationId.get()).as("messageId should not be empty").isNotNull().isNotEmpty();
      checkedOutMessageId.set(causationId.get());

      assertThat(correlationId).as("correlationId should be present").isNotNull();
      assertThat(correlationId.get()).as("correlationId should match initial").isEqualTo(initiatedCorrelationId.get());
      checkedOutCorrelationId.set(correlationId.get());

      assertThat(causationId).as("causationId should be present for subsequent events").isNotNull();
      assertThat(causationId.get()).as("causationId should not be empty").isNotNull().isNotEmpty();
      checkedOutCausationId.set(causationId.get());

      assertThat(currentSpan).as("Current span should be present in handler").isNotNull();
      assertThat(currentSpan.context().traceId()).as("TraceId should be present").isNotEmpty();
      assertThat(currentSpan.context().spanId()).as("SpanId should be present").isNotEmpty();
      checkedOutTraceId.set(currentSpan.context().traceId());
      checkedOutSpanId.set(currentSpan.context().spanId());
    });

    UUID guestStayId = UUID.randomUUID();
    guestStayFacade.checkInGuest(new CheckInGuest(guestStayId, now.minusDays(1)));
    publishedMessages.reset();

    UUID groupCheckoutId = UUID.randomUUID();
    UUID clerkId = UUID.randomUUID();
    groupCheckoutFacade.initiateGroupCheckout(
      new InitiateGroupCheckout(groupCheckoutId, clerkId, new UUID[]{guestStayId}, now)
    );

    await()
      .atMost(Duration.ofSeconds(100000))
      .untilAsserted(() -> {
        publishedMessages.shouldReceiveMessages(List.of(
          new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, new UUID[]{guestStayId}, now),
          new CheckOutGuest(guestStayId, now, groupCheckoutId),
          new GuestStayAccountEvent.GuestCheckedOut(guestStayId, now, groupCheckoutId)
        ));

        assertThat(initiatedMessageId.get()).as("Initiated messageId should be captured").isNotNull();
        assertThat(initiatedCorrelationId.get()).as("Initiated correlationId should be captured").isNotNull();
        assertThat(initiatedTraceId.get()).as("Initiated traceId should be captured").isNotNull();
        assertThat(checkedOutMessageId.get()).as("CheckedOut messageId should be captured").isNotNull();
        assertThat(checkedOutCorrelationId.get()).as("CheckedOut correlationId should be captured").isNotNull();
        assertThat(checkedOutCausationId.get()).as("CheckedOut causationId should be captured").isNotNull();
        assertThat(checkedOutTraceId.get()).as("CheckedOut traceId should be captured").isNotNull();
      });

    assertThat(initiatedMessageId.get())
      .as("messageId should be unique between messages")
      .isNotEqualTo(checkedOutMessageId.get());

    assertThat(checkedOutCorrelationId.get())
      .as("correlationId should be preserved across message chain")
      .isEqualTo(initiatedCorrelationId.get());

    assertThat(checkedOutTraceId.get())
      .as("traceId should be propagated across message chain")
      .isEqualTo(initiatedTraceId.get());

    assertThat(checkedOutSpanId.get())
      .as("spanId should be different for each message")
      .isNotEqualTo(initiatedSpanId.get());

    assertThat(checkedOutCausationId.get())
      .as("causationId should NOT equal the initiatedMessageId (it should be the CheckOutGuest command's messageId)")
      .isNotEqualTo(initiatedMessageId.get());

    var finishedSpans = spanExporter.getFinishedSpanItems();
    assertThat(finishedSpans)
      .as("OpenTelemetry spans should be created for Kafka operations")
      .hasSizeGreaterThan(0);

    var tracesInExporter = finishedSpans.stream()
      .filter(span -> span.getTraceId().equals(initiatedTraceId.get()))
      .toList();

    assertThat(tracesInExporter)
      .as("Spans from the business process should be captured in exporter")
      .isNotEmpty();
  }
}
