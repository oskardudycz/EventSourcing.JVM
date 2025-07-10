package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.groupcheckouts.GroupCheckoutEvent;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.groupcheckouts.GroupCheckoutFacade;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.gueststayaccounts.GuestStayAccountFacade;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;

@SpringBootTest(classes = {
    KafkaSolution2TestApplication.class
})
@Testcontainers
@TestPropertySource(properties = "messaging.type=kafka")
public class BusinessProcessTests {

  @Container
  static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    registry.add("app.kafka.topic", () -> "hotel-financial-solution2");
  }

  @Autowired
  private MessageBus messageBus;
  private MessageCatcher publishedMessages;
  @Autowired
  private GuestStayAccountFacade guestStayFacade;
  @Autowired
  private GroupCheckoutFacade groupCheckoutFacade;
  private Faker faker;
  private OffsetDateTime now;

  @BeforeEach
  public void beforeEach() {
    publishedMessages = new MessageCatcher();
    faker = new Faker();
    now = OffsetDateTime.now();

    messageBus.clearMiddleware();
    messageBus.use(publishedMessages::catchMessage);
    publishedMessages.reset();
  }

  @Test
  public void groupCheckoutForMultipleGuestStayWithoutPaymentsAndCharges_ShouldComplete() {
    // Given
    UUID[] guestStays = new UUID[] { UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID() };

    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[0], now.minusDays(1)));
    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[1], now.minusDays(1)));
    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[2], now.minusDays(1)));
    publishedMessages.reset();
    // And
    var groupCheckoutId = UUID.randomUUID();
    var clerkId = UUID.randomUUID();
    var command = new InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now);

    // When
    groupCheckoutFacade.initiateGroupCheckout(command);


    // Then
    await()
      .atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> publishedMessages.shouldReceiveMessages(new Object[] {
      new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now),
      new CheckOutGuest(guestStays[0], now, groupCheckoutId),
      new CheckOutGuest(guestStays[1], now, groupCheckoutId),
      new CheckOutGuest(guestStays[2], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[0], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[1], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[2], now, groupCheckoutId),
      new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[0], now),
      new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[1], now),
      new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[2], now),
      new GroupCheckoutEvent.GuestCheckoutCompleted(groupCheckoutId, guestStays[0], now),
      new GroupCheckoutEvent.GuestCheckoutCompleted(groupCheckoutId, guestStays[1], now),
      new GroupCheckoutEvent.GuestCheckoutCompleted(groupCheckoutId, guestStays[2], now),
      new GroupCheckoutEvent.GroupCheckoutCompleted(groupCheckoutId, guestStays, now)
    }));
  }

  @Test
  public void groupCheckoutForMultipleGuestStayWithAllStaysSettled_ShouldComplete() {
    // Given
    UUID[] guestStays = new UUID[] { UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID() };
    double[] amounts = new double[] {
      faker.number().randomDouble(2, 10, 1000),
      faker.number().randomDouble(2, 10, 1000),
      faker.number().randomDouble(2, 10, 1000)
    };

    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[0], now.minusDays(1)));
    guestStayFacade.recordCharge(new RecordCharge(guestStays[0], amounts[0], now.minusHours(2)));
    guestStayFacade.recordPayment(new RecordPayment(guestStays[0], amounts[0], now.minusHours(1)));

    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[1], now.minusDays(1)));
    guestStayFacade.recordPayment(new RecordPayment(guestStays[1], amounts[1], now.minusHours(1)));
    guestStayFacade.recordCharge(new RecordCharge(guestStays[1], amounts[1], now.minusHours(2)));

    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[2], now.minusDays(1)));
    guestStayFacade.recordCharge(new RecordCharge(guestStays[0], amounts[2], now.minusHours(2)));
    guestStayFacade.recordPayment(new RecordPayment(guestStays[0], amounts[2] / 2, now.minusHours(1)));
    guestStayFacade.recordPayment(new RecordPayment(guestStays[0], amounts[2] / 2, now.minusHours(1)));
    publishedMessages.reset();
    // And
    var groupCheckoutId = UUID.randomUUID();
    var clerkId = UUID.randomUUID();
    var command = new InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now);

    // When
    groupCheckoutFacade.initiateGroupCheckout(command);


    // Then
    await()
      .atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> publishedMessages.shouldReceiveMessages(new Object[] {
      new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now),
      new CheckOutGuest(guestStays[0], now, groupCheckoutId),
      new CheckOutGuest(guestStays[1], now, groupCheckoutId),
      new CheckOutGuest(guestStays[2], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[0], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[1], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[2], now, groupCheckoutId),
      new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[0], now),
      new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[1], now),
      new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[2], now),
      new GroupCheckoutEvent.GuestCheckoutCompleted(groupCheckoutId, guestStays[0], now),
      new GroupCheckoutEvent.GuestCheckoutCompleted(groupCheckoutId, guestStays[1], now),
      new GroupCheckoutEvent.GuestCheckoutCompleted(groupCheckoutId, guestStays[2], now),
      new GroupCheckoutEvent.GroupCheckoutCompleted(groupCheckoutId, guestStays, now)
    }));
  }

  @Test
  public void groupCheckoutForMultipleGuestStayWithOneSettledAndRestUnsettled_ShouldFail() {
    // Given
    UUID[] guestStays = new UUID[] { UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID() };
    double[] amounts = new double[] {
      faker.number().randomDouble(2, 10, 1000),
      faker.number().randomDouble(2, 10, 1000),
      faker.number().randomDouble(2, 10, 1000)
    };

    // 🟢 settled
    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[0], now.minusDays(1)));
    guestStayFacade.recordCharge(new RecordCharge(guestStays[0], amounts[0], now.minusHours(2)));
    guestStayFacade.recordPayment(new RecordPayment(guestStays[0], amounts[0], now.minusHours(1)));

    // 🛑 payment without charge
    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[1], now.minusDays(1)));
    guestStayFacade.recordPayment(new RecordPayment(guestStays[1], amounts[1], now.minusHours(1)));

    // 🛑 payment without charge
    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[2], now.minusDays(1)));
    guestStayFacade.recordCharge(new RecordCharge(guestStays[2], amounts[2], now.minusHours(2)));
    guestStayFacade.recordPayment(new RecordPayment(guestStays[2], amounts[2] / 2, now.minusHours(1)));
    publishedMessages.reset();
    // And
    var groupCheckoutId = UUID.randomUUID();
    var clerkId = UUID.randomUUID();
    var command = new InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now);

    // When
    groupCheckoutFacade.initiateGroupCheckout(command);


    // Then
    await()
      .atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> publishedMessages.shouldReceiveMessages(new Object[] {
      new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now),
      new CheckOutGuest(guestStays[0], now, groupCheckoutId),
      new CheckOutGuest(guestStays[1], now, groupCheckoutId),
      new CheckOutGuest(guestStays[2], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[0], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckoutFailed(guestStays[1],
        GuestStayAccountEvent.GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED, now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckoutFailed(guestStays[2],
        GuestStayAccountEvent.GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED, now, groupCheckoutId),
      new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[0], now),
      new RecordGuestCheckoutFailure(groupCheckoutId, guestStays[1], now),
      new RecordGuestCheckoutFailure(groupCheckoutId, guestStays[2], now),
      new GroupCheckoutEvent.GuestCheckoutCompleted(groupCheckoutId, guestStays[0], now),
      new GroupCheckoutEvent.GuestCheckoutFailed(groupCheckoutId, guestStays[1], now),
      new GroupCheckoutEvent.GuestCheckoutFailed(groupCheckoutId, guestStays[2], now),
      new GroupCheckoutEvent.GroupCheckoutFailed(
        groupCheckoutId,
        new UUID[] {guestStays[0]},
        new UUID[] {guestStays[1], guestStays[2]},
        now
      )
    }));
  }

  @Test
  public void groupCheckoutForMultipleGuestStayWithAllUnsettled_ShouldFail() {
    // Given
    UUID[] guestStays = new UUID[] { UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID() };
    double[] amounts = new double[] {
      faker.number().randomDouble(2, 10, 1000),
      faker.number().randomDouble(2, 10, 1000),
      faker.number().randomDouble(2, 10, 1000)
    };

    // 🛑 charge without payment
    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[0], now.minusDays(1)));
    guestStayFacade.recordCharge(new RecordCharge(guestStays[0], amounts[0], now.minusHours(2)));

    // 🛑 payment without charge
    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[1], now.minusDays(1)));
    guestStayFacade.recordPayment(new RecordPayment(guestStays[1], amounts[1], now.minusHours(1)));

    // 🛑 payment without charge
    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[2], now.minusDays(1)));
    guestStayFacade.recordCharge(new RecordCharge(guestStays[2], amounts[2], now.minusHours(2)));
    guestStayFacade.recordPayment(new RecordPayment(guestStays[2], amounts[2] / 2, now.minusHours(1)));
    publishedMessages.reset();
    // And
    var groupCheckoutId = UUID.randomUUID();
    var clerkId = UUID.randomUUID();
    var command = new InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now);

    // When
    groupCheckoutFacade.initiateGroupCheckout(command);


    // Then
    await()
      .atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> publishedMessages.shouldReceiveMessages(new Object[] {
      new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now),
      new CheckOutGuest(guestStays[0], now, groupCheckoutId),
      new CheckOutGuest(guestStays[1], now, groupCheckoutId),
      new CheckOutGuest(guestStays[2], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckoutFailed(guestStays[0],
        GuestStayAccountEvent.GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED, now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckoutFailed(guestStays[1],
        GuestStayAccountEvent.GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED, now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckoutFailed(guestStays[2],
        GuestStayAccountEvent.GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED, now, groupCheckoutId),
      new RecordGuestCheckoutFailure(groupCheckoutId, guestStays[0], now),
      new RecordGuestCheckoutFailure(groupCheckoutId, guestStays[1], now),
      new RecordGuestCheckoutFailure(groupCheckoutId, guestStays[2], now),
      new GroupCheckoutEvent.GuestCheckoutFailed(groupCheckoutId, guestStays[0], now),
      new GroupCheckoutEvent.GuestCheckoutFailed(groupCheckoutId, guestStays[1], now),
      new GroupCheckoutEvent.GuestCheckoutFailed(groupCheckoutId, guestStays[2], now),
      new GroupCheckoutEvent.GroupCheckoutFailed(
        groupCheckoutId,
        new UUID[] {},
        new UUID[] {guestStays[0], guestStays[1], guestStays[2]},
        now
      )
    }));
  }
}

