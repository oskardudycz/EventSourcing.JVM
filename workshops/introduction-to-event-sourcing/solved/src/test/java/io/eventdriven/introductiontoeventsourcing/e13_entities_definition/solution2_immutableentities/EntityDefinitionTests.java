package io.eventdriven.introductiontoeventsourcing.e13_entities_definition.solution2_immutableentities;

import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.core.EventStore;
import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.core.MessageCatcher;
import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand;
import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent;
import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.solution2_immutableentities.gueststayaccounts.GuestStayAccountFacade;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;


public class EntityDefinitionTests {
  private EventStore eventStore;
  private MessageCatcher publishedEvents;
  private GuestStayAccountFacade guestStayFacade;
  private Faker faker;
  private OffsetDateTime now;

  @BeforeEach
  public void setUp() {
    eventStore = new EventStore();
    publishedEvents = new MessageCatcher();
    guestStayFacade = new GuestStayAccountFacade(eventStore);
    faker = new Faker();
    now = OffsetDateTime.now();
    eventStore.use(publishedEvents::catchMessage);
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
}
