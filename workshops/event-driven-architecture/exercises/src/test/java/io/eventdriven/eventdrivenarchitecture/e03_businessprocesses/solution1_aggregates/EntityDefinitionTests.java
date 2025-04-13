package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution1_aggregates;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.EventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade.GuestStayAccountCommand;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

public class EntityDefinitionTests {

  private Database database;
  private EventBus eventBus;
  private MessageCatcher publishedEvents;
  private GuestStayAccountFacade guestStayFacade;
  private Faker faker;
  private OffsetDateTime now;

  @BeforeEach
  public void setUp() {
    database = new Database();
    eventBus = new EventBus();
    publishedEvents = new MessageCatcher();
    guestStayFacade = new GuestStayAccountFacade(database, eventBus);
    faker = new Faker();
    now = OffsetDateTime.now();
    eventBus.use(publishedEvents::catchMessage);
  }

  @Test
  @Tag("Exercise")
  public void checkingInGuest_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();
    var command = new GuestStayAccountCommand.CheckInGuest(guestStayId, now);
    publishedEvents.reset();

    // When
    guestStayFacade.checkInGuest(command);

    // Then
    publishedEvents.shouldReceiveSingleEvent(new GuestStayAccountEvent.GuestCheckedIn(guestStayId, now));
  }

  @Test
  @Tag("Exercise")
  public void recordingChargeForCheckedInGuest_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();
    guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(guestStayId, now.minusDays(1)));
    publishedEvents.reset();
    // And
    var amount = faker.number().randomDouble(2, 10, 1000);
    var command = new GuestStayAccountCommand.RecordCharge(guestStayId, amount, now);

    // When
    guestStayFacade.recordCharge(command);

    // Then
    publishedEvents.shouldReceiveSingleEvent(new GuestStayAccountEvent.ChargeRecorded(guestStayId, amount, now));
  }

  @Test
  @Tag("Exercise")
  public void recordingPaymentForCheckedInGuest_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();
    guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(guestStayId, now.minusDays(1)));
    publishedEvents.reset();
    // And
    var amount = faker.number().randomDouble(2, 10, 1000);
    var command = new GuestStayAccountCommand.RecordPayment(guestStayId, amount, now);

    // When
    guestStayFacade.recordPayment(command);

    // Then
    publishedEvents.shouldReceiveSingleEvent(new GuestStayAccountEvent.PaymentRecorded(guestStayId, amount, now));
  }

  @Test
  @Tag("Exercise")
  public void recordingPaymentForCheckedInGuestWithCharge_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();
    guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(guestStayId, now.minusDays(1)));
    guestStayFacade.recordCharge(new GuestStayAccountCommand.RecordCharge(guestStayId, faker.number().randomDouble(2, 10, 1000), now.minusHours(1)));
    publishedEvents.reset();
    // And
    var amount = faker.number().randomDouble(2, 10, 1000);
    var command = new GuestStayAccountCommand.RecordPayment(guestStayId, amount, now);

    // When
    guestStayFacade.recordPayment(command);

    // Then
    publishedEvents.shouldReceiveSingleEvent(new GuestStayAccountEvent.PaymentRecorded(guestStayId, amount, now));
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
    publishedEvents.reset();
    // And
    var command = new GuestStayAccountCommand.CheckOutGuest(guestStayId, now);

    // When
    guestStayFacade.checkOutGuest(command);

    // Then
    publishedEvents.shouldReceiveSingleEvent(new GuestStayAccountEvent.GuestCheckedOut(guestStayId, now, null));
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
    publishedEvents.reset();
    // And
    var command = new GuestStayAccountCommand.CheckOutGuest(guestStayId, now);

    // When
    try {
      guestStayFacade.checkOutGuest(command);
    } catch (Exception exc) {
      System.out.println(exc.getMessage());
    }

    // Then
    publishedEvents.shouldReceiveSingleEvent(
      new GuestStayAccountEvent.GuestCheckoutFailed(
        guestStayId,
        GuestStayAccountEvent.GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED,
        now,
        null
      )
    );
  }
}
