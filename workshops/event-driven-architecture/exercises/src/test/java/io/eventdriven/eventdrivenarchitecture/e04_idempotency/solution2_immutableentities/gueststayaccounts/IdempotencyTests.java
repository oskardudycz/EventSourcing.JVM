package io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e04_idempotency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.ChargeRecorded;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.GuestCheckedIn;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.GuestCheckedOut;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.PaymentRecorded;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;

public class IdempotencyTests {
  @Test
  @Tag("Exercise")
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
  @Tag("Exercise")
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
  @Tag("Exercise")
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
  @Tag("Exercise")
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

  private MessageCatcher publishedMessages;
  private GuestStayAccountFacade guestStayFacade;
  private OffsetDateTime now;

  @BeforeEach
  public void setUp() {
    Database database = new Database();
    MessageBus messageBus = new InMemoryMessageBus();
    publishedMessages = new MessageCatcher();
    guestStayFacade = new GuestStayAccountFacade(database.collection(GuestStayAccount.class), messageBus);
    now = OffsetDateTime.now();

    messageBus.use(publishedMessages::catchMessage);
  }

  private UUID checkInGuest() {
    var guestStayId = UUID.randomUUID();
    guestStayFacade.checkInGuest(new CheckInGuest(guestStayId, now));
    publishedMessages.reset();
    return guestStayId;
  }
}
