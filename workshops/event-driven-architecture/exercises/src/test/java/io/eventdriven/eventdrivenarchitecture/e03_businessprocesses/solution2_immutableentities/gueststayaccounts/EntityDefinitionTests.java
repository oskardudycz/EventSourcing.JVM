package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;


public class EntityDefinitionTests {

  private Database database;
  private MessageBus messageBus;
  private MessageCatcher publishedMessages;
  private GuestStayAccountFacade guestStayFacade;
  private Faker faker;
  private OffsetDateTime now;

  @BeforeEach
  public void setUp() {
    database = new Database();
    messageBus = new InMemoryMessageBus();
    publishedMessages = new MessageCatcher();
    guestStayFacade = new GuestStayAccountFacade(database, messageBus);
    faker = new Faker();
    now = OffsetDateTime.now();
    messageBus.use(publishedMessages::catchMessage);
  }

  @Test
  @Tag("Exercise")
  public void checkingInGuest_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();
    var command = new GuestStayAccountCommand.CheckInGuest(guestStayId, now);
    publishedMessages.reset();

    // When
    guestStayFacade.checkInGuest(command);

    // Then
    publishedMessages.shouldReceiveSingleMessage(new GuestStayAccountEvent.GuestCheckedIn(guestStayId, now));
  }

  @Test
  @Tag("Exercise")
  public void recordingChargeForCheckedInGuest_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();
    guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(guestStayId, now.minusDays(1)));
    publishedMessages.reset();
    // And
    var amount = faker.number().randomDouble(2, 10, 1000);
    var command = new GuestStayAccountCommand.RecordCharge(guestStayId, amount, now);

    // When
    guestStayFacade.recordCharge(command);

    // Then
    publishedMessages.shouldReceiveSingleMessage(new GuestStayAccountEvent.ChargeRecorded(guestStayId, amount, now));
  }

  @Test
  @Tag("Exercise")
  public void recordingPaymentForCheckedInGuest_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();
    guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(guestStayId, now.minusDays(1)));
    publishedMessages.reset();
    // And
    var amount = faker.number().randomDouble(2, 10, 1000);
    var command = new GuestStayAccountCommand.RecordPayment(guestStayId, amount, now);

    // When
    guestStayFacade.recordPayment(command);

    // Then
    publishedMessages.shouldReceiveSingleMessage(new GuestStayAccountEvent.PaymentRecorded(guestStayId, amount, now));
  }

  @Test
  @Tag("Exercise")
  public void recordingPaymentForCheckedInGuestWithCharge_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();
    guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(guestStayId, now.minusDays(1)));
    guestStayFacade.recordCharge(new GuestStayAccountCommand.RecordCharge(guestStayId, faker.number().randomDouble(2, 10, 1000), now.minusHours(1)));
    publishedMessages.reset();
    // And
    var amount = faker.number().randomDouble(2, 10, 1000);
    var command = new GuestStayAccountCommand.RecordPayment(guestStayId, amount, now);

    // When
    guestStayFacade.recordPayment(command);

    // Then
    publishedMessages.shouldReceiveSingleMessage(new GuestStayAccountEvent.PaymentRecorded(guestStayId, amount, now));
  }

  @Test
  @Tag("Exercise")
  public void checkingOutGuestWithSettledBalance_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();

    var amount = faker.number().randomDouble(2, 10, 1000);
    guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(guestStayId, now.minusDays(1)));
    guestStayFacade.recordCharge(new GuestStayAccountCommand.RecordCharge(guestStayId, amount, now.minusHours(2)));
    guestStayFacade.recordPayment(new GuestStayAccountCommand.RecordPayment(guestStayId, amount, now.minusHours(1)));
    publishedMessages.reset();
    // And
    var command = new GuestStayAccountCommand.CheckOutGuest(guestStayId, now);

    // When
    guestStayFacade.checkOutGuest(command);

    // Then
    publishedMessages.shouldReceiveSingleMessage(new GuestStayAccountEvent.GuestCheckedOut(guestStayId, now, null));
  }

  @Test
  @Tag("Exercise")
  public void checkingOutGuestWithUnsettledBalance_FailsWithGuestCheckoutFailed() {
    // Given
    var guestStayId = UUID.randomUUID();

    var amount = faker.number().randomDouble(2, 10, 1000);
    guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(guestStayId, now.minusDays(1)));
    guestStayFacade.recordCharge(new GuestStayAccountCommand.RecordCharge(guestStayId, amount + 10, now.minusHours(2)));
    guestStayFacade.recordPayment(new GuestStayAccountCommand.RecordPayment(guestStayId, amount, now.minusHours(1)));
    publishedMessages.reset();
    // And
    var command = new GuestStayAccountCommand.CheckOutGuest(guestStayId, now);

    // When
    try {
      guestStayFacade.checkOutGuest(command);
    } catch (Exception exc) {
      System.out.println(exc.getMessage());
    }

    // Then
    publishedMessages.shouldReceiveSingleMessage(
      new GuestStayAccountEvent.GuestCheckoutFailed(
        guestStayId,
        GuestStayAccountEvent.GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED,
        now,
        null
      )
    );
  }
}
