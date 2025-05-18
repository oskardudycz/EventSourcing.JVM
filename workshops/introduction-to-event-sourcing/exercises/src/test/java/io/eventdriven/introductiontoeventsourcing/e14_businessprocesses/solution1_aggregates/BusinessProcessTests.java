package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution1_aggregates;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.CommandBus;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.Database;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.EventStore;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.MessageCatcher;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution1_aggregates.groupcheckouts.GroupCheckoutFacade;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution1_aggregates.groupcheckouts.GroupCheckoutEvent;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;
import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade.GuestStayAccountCommand.*;
import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution1_aggregates.groupcheckouts.GroupCheckoutsConfig.configureGroupCheckouts;
import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution1_aggregates.gueststayaccounts.GuestStayAccountsConfig.configureGuestStayAccounts;

public class BusinessProcessTests {

  private Database database;
  private EventStore eventStore;
  private CommandBus commandBus;
  private MessageCatcher publishedMessages;
  private GuestStayAccountFacade guestStayFacade;
  private GroupCheckoutFacade groupCheckoutFacade;
  private Faker faker;
  private OffsetDateTime now;

  @BeforeEach
  public void setUp() {
    database = new Database();
    eventStore = new EventStore();
    commandBus = new CommandBus();
    publishedMessages = new MessageCatcher();
    guestStayFacade = new GuestStayAccountFacade(database, eventStore);
    groupCheckoutFacade = new GroupCheckoutFacade(database, eventStore);
    faker = new Faker();
    now = OffsetDateTime.now();

    eventStore.use(publishedMessages::catchMessage);
    commandBus.use(publishedMessages::catchMessage);

    configureGroupCheckouts(eventStore, commandBus, groupCheckoutFacade);
    configureGuestStayAccounts(commandBus, guestStayFacade);
  }

  @Test
  @Tag("Exercise")
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
    publishedMessages.shouldReceiveMessages(new Object[] {
      new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now),
      new CheckOutGuest(guestStays[0], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[0], now, groupCheckoutId),
      new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[0], now),
      new GroupCheckoutEvent.GuestCheckoutCompleted(groupCheckoutId, guestStays[0], now),
      new CheckOutGuest(guestStays[1], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[1], now, groupCheckoutId),
      new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[1], now),
      new GroupCheckoutEvent.GuestCheckoutCompleted(groupCheckoutId, guestStays[1], now),
      new CheckOutGuest(guestStays[2], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[2], now, groupCheckoutId),
      new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[2], now),
      new GroupCheckoutEvent.GuestCheckoutCompleted(groupCheckoutId, guestStays[2], now),
      new GroupCheckoutEvent.GroupCheckoutCompleted(groupCheckoutId, guestStays, now)
    });
  }

  @Test
  @Tag("Exercise")
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
    publishedMessages.shouldReceiveMessages(new Object[] {
      new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now),
      new CheckOutGuest(guestStays[0], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[0], now, groupCheckoutId),
      new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[0], now),
      new GroupCheckoutEvent.GuestCheckoutCompleted(groupCheckoutId, guestStays[0], now),
      new CheckOutGuest(guestStays[1], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[1], now, groupCheckoutId),
      new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[1], now),
      new GroupCheckoutEvent.GuestCheckoutCompleted(groupCheckoutId, guestStays[1], now),
      new CheckOutGuest(guestStays[2], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[2], now, groupCheckoutId),
      new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[2], now),
      new GroupCheckoutEvent.GuestCheckoutCompleted(groupCheckoutId, guestStays[2], now),
      new GroupCheckoutEvent.GroupCheckoutCompleted(groupCheckoutId, guestStays, now)
    });
  }

  @Test
  @Tag("Exercise")
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
    publishedMessages.shouldReceiveMessages(new Object[] {
      new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now),
      new CheckOutGuest(guestStays[0], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[0], now, groupCheckoutId),
      new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[0], now),
      new GroupCheckoutEvent.GuestCheckoutCompleted(groupCheckoutId, guestStays[0], now),
      new CheckOutGuest(guestStays[1], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckoutFailed(guestStays[1],
        GuestStayAccountEvent.GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED, now, groupCheckoutId),
      new RecordGuestCheckoutFailure(groupCheckoutId, guestStays[1], now),
      new GroupCheckoutEvent.GuestCheckoutFailed(groupCheckoutId, guestStays[1], now),
      new CheckOutGuest(guestStays[2], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckoutFailed(guestStays[2],
        GuestStayAccountEvent.GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED, now, groupCheckoutId),
      new RecordGuestCheckoutFailure(groupCheckoutId, guestStays[2], now),
      new GroupCheckoutEvent.GuestCheckoutFailed(groupCheckoutId, guestStays[2], now),
      new GroupCheckoutEvent.GroupCheckoutFailed(
        groupCheckoutId,
        new UUID[] {guestStays[0]},
        new UUID[] {guestStays[1], guestStays[2]},
        now
      )
    });
  }

  @Test
  @Tag("Exercise")
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
    publishedMessages.shouldReceiveMessages(new Object[] {
      new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now),
      new CheckOutGuest(guestStays[0], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckoutFailed(guestStays[0],
        GuestStayAccountEvent.GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED, now, groupCheckoutId),
      new RecordGuestCheckoutFailure(groupCheckoutId, guestStays[0], now),
      new GroupCheckoutEvent.GuestCheckoutFailed(groupCheckoutId, guestStays[0], now),
      new CheckOutGuest(guestStays[1], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckoutFailed(guestStays[1],
        GuestStayAccountEvent.GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED, now, groupCheckoutId),
      new RecordGuestCheckoutFailure(groupCheckoutId, guestStays[1], now),
      new GroupCheckoutEvent.GuestCheckoutFailed(groupCheckoutId, guestStays[1], now),
      new CheckOutGuest(guestStays[2], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckoutFailed(guestStays[2],
        GuestStayAccountEvent.GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED, now, groupCheckoutId),
      new RecordGuestCheckoutFailure(groupCheckoutId, guestStays[2], now),
      new GroupCheckoutEvent.GuestCheckoutFailed(groupCheckoutId, guestStays[2], now),
      new GroupCheckoutEvent.GroupCheckoutFailed(
        groupCheckoutId,
        new UUID[] {},
        new UUID[] {guestStays[0], guestStays[1], guestStays[2]},
        now
      )
    });
  }
}

