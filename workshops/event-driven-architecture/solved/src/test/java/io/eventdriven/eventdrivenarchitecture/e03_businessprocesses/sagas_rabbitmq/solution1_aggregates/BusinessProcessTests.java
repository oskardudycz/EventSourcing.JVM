package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.ICommandBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.IEventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts.GroupCheckoutFacade;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts.GroupCheckoutEvent;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade.GuestStayAccountCommand.*;

@SpringBootTest(classes = {
    RabbitMQSolution1TestApplication.class
})
@Testcontainers
@TestPropertySource(properties = "messaging.type=rabbitmq")
public class BusinessProcessTests {

  @Container
  static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3-management");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
    registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
    registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
    registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
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
  public void groupCheckoutForMultipleGuestStayWithoutPaymentsAndCharges_ShouldComplete() throws InterruptedException {
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
  public void groupCheckoutForMultipleGuestStayWithAllStaysSettled_ShouldComplete() throws InterruptedException {
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
  public void groupCheckoutForMultipleGuestStayWithOneSettledAndRestUnsettled_ShouldFail() throws InterruptedException {
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
  public void groupCheckoutForMultipleGuestStayWithAllUnsettled_ShouldFail() throws InterruptedException {
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


