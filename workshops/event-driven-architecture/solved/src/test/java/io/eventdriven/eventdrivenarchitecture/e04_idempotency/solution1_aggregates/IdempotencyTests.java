package io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates;

import io.eventdriven.eventdrivenarchitecture.e04_idempotency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.groupcheckouts.GroupCheckout;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.groupcheckouts.GroupCheckoutEvent.GroupCheckoutCompleted;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.groupcheckouts.GroupCheckoutEvent.GroupCheckoutInitiated;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.groupcheckouts.GroupCheckoutEvent.GuestCheckoutCompletionRecorded;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.groupcheckouts.GroupCheckoutEvent.GuestCheckoutFailureRecorded;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.groupcheckouts.GroupCheckoutFacade;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.gueststayaccounts.GuestStayAccount;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.ChargeRecorded;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.GuestCheckedIn;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.GuestCheckedOut;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.PaymentRecorded;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.groupcheckouts.GroupCheckoutsConfig.configureGroupCheckouts;
import static io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade.GuestStayAccountCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.gueststayaccounts.GuestStayAccountsConfig.configureGuestStayAccounts;

public class IdempotencyTests {
  private MessageCatcher publishedMessages;
  private GuestStayAccountFacade guestStayFacade;
  private GroupCheckoutFacade groupCheckoutFacade;
  private OffsetDateTime now;

  @BeforeEach
  public void setUp() {
    Database database = new Database();
    MessageBus messageBus = new InMemoryMessageBus();
    publishedMessages = new MessageCatcher();
    guestStayFacade = new GuestStayAccountFacade(database.collection(GuestStayAccount.class), messageBus);
    groupCheckoutFacade = new GroupCheckoutFacade(database.collection(GroupCheckout.class), messageBus);
    now = OffsetDateTime.now();

    messageBus.use(publishedMessages::catchMessage);
  }

  @Test
  public void groupCheckoutIsInitiatedOnceForTheSameId() {
    // Given
    var groupCheckoutId = UUID.randomUUID();
    var clerkId = UUID.randomUUID();
    UUID[] guestStays = new UUID[]{UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
    var command = new InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now);
    // And
    groupCheckoutFacade.initiateGroupCheckout(command);

    // When
    groupCheckoutFacade.initiateGroupCheckout(command);

    // Then
    publishedMessages.shouldReceiveSingleMessage(
      new GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now)
    );
  }

  @Test
  public void groupCheckoutRecordsOnceGuestCheckoutCompletion() {
    // Given
    var groupCheckoutId = UUID.randomUUID();
    var clerkId = UUID.randomUUID();
    UUID[] guestStays = new UUID[]{UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
    // And
    groupCheckoutFacade.initiateGroupCheckout(
      new InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now.minusDays(1))
    );
    publishedMessages.reset();

    var command = new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[0], now);
    groupCheckoutFacade.recordGuestCheckoutCompletion(command);

    // When
    groupCheckoutFacade.recordGuestCheckoutCompletion(command);

    // Then
    publishedMessages.shouldReceiveSingleMessage(
      new GuestCheckoutCompletionRecorded(groupCheckoutId, guestStays[0], now)
    );
  }

  @Test
  public void groupCheckoutRecordsOnceGuestCheckoutFailure() {
    // Given
    var groupCheckoutId = UUID.randomUUID();
    var clerkId = UUID.randomUUID();
    UUID[] guestStays = new UUID[]{UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
    // And
    groupCheckoutFacade.initiateGroupCheckout(
      new InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now.minusDays(1))
    );
    publishedMessages.reset();

    var command = new RecordGuestCheckoutFailure(groupCheckoutId, guestStays[0], now);
    groupCheckoutFacade.recordGuestCheckoutFailure(command);

    // When
    groupCheckoutFacade.recordGuestCheckoutFailure(command);

    // Then
    publishedMessages.shouldReceiveSingleMessage(
      new GuestCheckoutFailureRecorded(groupCheckoutId, guestStays[0], now)
    );
  }

  @Test
  public void guestIsCheckedInOnceForTheSameGuestStayAccount() {
    // Given
    var guestStayId = UUID.randomUUID();
    var command = new CheckInGuest(guestStayId, now);
    // And
    guestStayFacade.checkInGuest(command);

    // When
    guestStayFacade.checkInGuest(command);

    // Then
    publishedMessages.shouldReceiveSingleMessage(new GuestCheckedIn(guestStayId, now));
  }

  @Test
  public void chargeIsRecordedOnceForTheSameChargeId() {
    // Given
    var guestStayId = checkInGuest();
    var chargeId = UUID.randomUUID();
    var command = new RecordCharge(guestStayId, chargeId, 100, now);
    // And
    guestStayFacade.recordCharge(command);

    // When
    guestStayFacade.recordCharge(command);

    // Then
    publishedMessages.shouldReceiveSingleMessage(new ChargeRecorded(guestStayId, chargeId, 100, now));
  }

  @Test
  public void paymentIsRecordedOnceForTheSamePaymentId() {
    // Given
    var guestStayId = checkInGuest();
    var paymentId = UUID.randomUUID();
    var command = new RecordPayment(guestStayId, paymentId, 100, now);
    // And
    guestStayFacade.recordPayment(command);

    // When
    guestStayFacade.recordPayment(command);

    // Then
    publishedMessages.shouldReceiveSingleMessage(new PaymentRecorded(guestStayId, paymentId, 100, now));
  }

  @Test
  public void guestIsCheckedOutOnceForTheSameGroupCheckout() {
    // Given
    var guestStayId = checkInGuest();
    var groupCheckoutId = UUID.randomUUID();
    var command = new CheckOutGuest(guestStayId, now, groupCheckoutId);
    // And
    guestStayFacade.checkOutGuest(command);

    // When
    guestStayFacade.checkOutGuest(command);

    // Then
    publishedMessages.shouldReceiveSingleMessage(new GuestCheckedOut(guestStayId, now, groupCheckoutId));
  }

  @Test
  public void groupCheckoutCompletesOnceWhenTheLastGuestCheckoutCompletionIsRecordedTwice() {
    // Given
    var groupCheckoutId = UUID.randomUUID();
    UUID[] guestStays = new UUID[]{UUID.randomUUID(), UUID.randomUUID()};
    groupCheckoutFacade.initiateGroupCheckout(
      new InitiateGroupCheckout(groupCheckoutId, UUID.randomUUID(), guestStays, now)
    );
    groupCheckoutFacade.recordGuestCheckoutCompletion(
      new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[0], now)
    );
    publishedMessages.reset();
    // And
    var command = new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[1], now);
    groupCheckoutFacade.recordGuestCheckoutCompletion(command);

    // When
    groupCheckoutFacade.recordGuestCheckoutCompletion(command);

    // Then
    publishedMessages.shouldReceiveMessages(
      List.of(
        new GuestCheckoutCompletionRecorded(groupCheckoutId, guestStays[1], now),
        new GroupCheckoutCompleted(groupCheckoutId, guestStays, now)
      )
    );
  }

  @Test
  public void groupCheckoutCompletesOnceWhenTheBusDeliversEveryMessageTwice() {
    // Given
    var database = new Database();
    var messageBus = new InMemoryMessageBus(true);
    var publishedMessages = new MessageCatcher();
    messageBus.use(publishedMessages::catchMessage);

    var guestStayFacade = new GuestStayAccountFacade(database.collection(GuestStayAccount.class), messageBus);
    var groupCheckoutFacade = new GroupCheckoutFacade(database.collection(GroupCheckout.class), messageBus);
    configureGroupCheckouts(messageBus, groupCheckoutFacade);
    configureGuestStayAccounts(messageBus, guestStayFacade);

    UUID[] guestStays = new UUID[]{UUID.randomUUID(), UUID.randomUUID()};
    for (var guestStayId : guestStays)
      guestStayFacade.checkInGuest(new CheckInGuest(guestStayId, now));
    publishedMessages.reset();
    // And
    var groupCheckoutId = UUID.randomUUID();
    var clerkId = UUID.randomUUID();

    // When
    groupCheckoutFacade.initiateGroupCheckout(new InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now));

    // Then
    publishedMessages.shouldReceiveMessages(
      List.of(
        new GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now),
        new CheckOutGuest(guestStays[0], now, groupCheckoutId),
        new GuestCheckedOut(guestStays[0], now, groupCheckoutId),
        new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[0], now),
        new GuestCheckoutCompletionRecorded(groupCheckoutId, guestStays[0], now),
        new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[0], now),
        new CheckOutGuest(guestStays[1], now, groupCheckoutId),
        new GuestCheckedOut(guestStays[1], now, groupCheckoutId),
        new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[1], now),
        new GuestCheckoutCompletionRecorded(groupCheckoutId, guestStays[1], now),
        new GroupCheckoutCompleted(groupCheckoutId, guestStays, now),
        new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[1], now),
        new CheckOutGuest(guestStays[0], now, groupCheckoutId),
        new CheckOutGuest(guestStays[1], now, groupCheckoutId)
      )
    );
  }

  private UUID checkInGuest() {
    var guestStayId = UUID.randomUUID();
    guestStayFacade.checkInGuest(new CheckInGuest(guestStayId, now));
    publishedMessages.reset();
    return guestStayId;
  }
}
