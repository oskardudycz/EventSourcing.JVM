package io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates;

import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core.Database;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core.EventBus;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core.EventCatcher;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.groupcheckouts.GroupCheckoutCommand.InitiateGroupCheckout;
import static io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.groupcheckouts.GroupCheckoutEvent.GroupCheckoutInitiated;
import static io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.gueststayaccounts.GuestStayAccountCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.*;

public class EntityDefinitionTests {
  private final Database database = new Database();
  private final EventBus eventBus = new EventBus();
  private final EventCatcher publishedEvents = new EventCatcher();
  private final GuestStayFacade guestStayFacade;
  private final Faker generate = new Faker();
  private final OffsetDateTime now = OffsetDateTime.now();

  public EntityDefinitionTests() {
    guestStayFacade = new GuestStayFacade(database, eventBus);
    eventBus.use(publishedEvents::catchEvent);
  }

  @BeforeEach
  void setUp() {
    publishedEvents.reset();
  }

  @Test
  void checkingInGuest_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();
    var command = new CheckInGuest(guestStayId, now);

    // When
    guestStayFacade.checkInGuest(command);

    // Then
    publishedEvents.shouldReceiveSingleEvent(new GuestCheckedIn(guestStayId, now));
  }

  @Test
  void recordingChargeForCheckedInGuest_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();
    guestStayFacade.checkInGuest(new CheckInGuest(guestStayId, now.minusDays(1)));
    publishedEvents.reset();
    // And
    var amount = generate.number().randomDouble(2, 1, 1000);
    var command = new RecordCharge(guestStayId, amount, now);

    // When
    guestStayFacade.recordCharge(command);

    // Then
    publishedEvents.shouldReceiveSingleEvent(new ChargeRecorded(guestStayId, amount, now));
  }

  @Test
  void recordingPaymentForCheckedInGuest_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();
    guestStayFacade.checkInGuest(new CheckInGuest(guestStayId, now.minusDays(1)));
    publishedEvents.reset();
    // And
    var amount = generate.number().randomDouble(2, 1, 1000);
    var command = new RecordPayment(guestStayId, amount, now);

    // When
    guestStayFacade.recordPayment(command);

    // Then
    publishedEvents.shouldReceiveSingleEvent(new PaymentRecorded(guestStayId, amount, now));
  }

  @Test
  void recordingPaymentForCheckedInGuestWithCharge_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();
    guestStayFacade.checkInGuest(new CheckInGuest(guestStayId, now.minusDays(1)));
    guestStayFacade.recordCharge(new RecordCharge(guestStayId, generate.number().randomDouble(2, 1, 1000), now.minusHours(1)));
    publishedEvents.reset();
    // And
    var amount = generate.number().randomDouble(2, 1, 1000);
    var command = new RecordPayment(guestStayId, amount, now);

    // When
    guestStayFacade.recordPayment(command);

    // Then
    publishedEvents.shouldReceiveSingleEvent(new PaymentRecorded(guestStayId, amount, now));
  }

  @Test
  void checkingOutGuestWithSettledBalance_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();
    var amount = generate.number().randomDouble(2, 1, 1000);

    guestStayFacade.checkInGuest(new CheckInGuest(guestStayId, now.minusDays(1)));
    guestStayFacade.recordCharge(new RecordCharge(guestStayId, amount, now.minusHours(2)));
    guestStayFacade.recordPayment(new RecordPayment(guestStayId, amount, now.minusHours(1)));
    publishedEvents.reset();
    // And
    var command = new CheckOutGuest(guestStayId, null, now);

    // When
    guestStayFacade.checkOutGuest(command);

    // Then
    publishedEvents.shouldReceiveSingleEvent(new GuestCheckedOut(guestStayId, null, now));
  }

  @Test
  void checkingOutGuestWithUnsettledBalance_FailsWithGuestCheckoutFailed() {
    // Given
    var guestStayId = UUID.randomUUID();
    var amount = generate.number().randomDouble(2, 1, 1000);

    guestStayFacade.checkInGuest(new CheckInGuest(guestStayId, now.minusDays(1)));
    guestStayFacade.recordCharge(new RecordCharge(guestStayId, amount + 10, now.minusHours(2)));
    guestStayFacade.recordPayment(new RecordPayment(guestStayId, amount, now.minusHours(1)));
    publishedEvents.reset();
    // And
    var command = new CheckOutGuest(guestStayId, null, now);

    // When
    try {
      guestStayFacade.checkOutGuest(command);
    } catch (Exception exc) {
      System.out.println(exc.getMessage());
    }

    // Then
    publishedEvents.shouldReceiveSingleEvent(
      new GuestStayAccountEvent.GuestCheckoutFailed(guestStayId, GuestCheckoutFailed.Reason.BalanceNotSettled, null, now)
    );
  }

  @Test
  void groupCheckoutForMultipleGuestStay_ShouldBeInitiated() {
    // Given
    var guestStays = new UUID[]{
      UUID.randomUUID(),
      UUID.randomUUID(),
      UUID.randomUUID()
    };

    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[0], now.minusDays(1)));
    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[1], now.minusDays(1)));
    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[2], now.minusDays(1)));
    publishedEvents.reset();
    // And
    var groupCheckoutId = UUID.randomUUID();
    var clerkId = UUID.randomUUID();
    var command = new InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now);

    // When
    guestStayFacade.initiateGroupCheckout(command);

    // Then
    publishedEvents.shouldReceiveSingleEvent(
      new GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now)
    );
  }
}
