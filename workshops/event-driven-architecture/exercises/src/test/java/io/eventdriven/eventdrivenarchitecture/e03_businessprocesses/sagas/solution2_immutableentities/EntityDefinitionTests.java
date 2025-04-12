package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.EventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayFacade;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayFacade.GroupCheckoutCommand;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayFacade.GuestStayAccountCommand;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.groupcheckouts.GroupCheckoutEvent;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

public class EntityDefinitionTests {

  private Database database;
  private EventBus eventBus;
  private MessageCatcher publishedEvents;
  private GuestStayFacade guestStayFacade;
  private Faker faker;
  private OffsetDateTime now;

  @BeforeEach
  public void setUp() {
    database = new Database();
    eventBus = new EventBus();
    publishedEvents = new MessageCatcher();
    guestStayFacade = new GuestStayFacade(database, eventBus);
    faker = new Faker();
    now = OffsetDateTime.now();
    eventBus.use(publishedEvents::catchMessage);
  }

  @Test
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

  @Test
  public void groupCheckoutForMultipleGuestStay_ShouldBeInitiated() {
    // Given
    UUID[] guestStays = new UUID[] { UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID() };

    guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(guestStays[0], now.minusDays(1)));
    guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(guestStays[1], now.minusDays(1)));
    guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(guestStays[2], now.minusDays(1)));
    publishedEvents.reset();
    // And
    var groupCheckoutId = UUID.randomUUID();
    var clerkId = UUID.randomUUID();
    var command = new GroupCheckoutCommand.InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now);

    // When
    guestStayFacade.initiateGroupCheckout(command);

    // Then
    publishedEvents.shouldReceiveSingleEvent(
      new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now)
    );
  }
}
