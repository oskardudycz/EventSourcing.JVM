package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckout;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckoutEvent;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckoutFacade;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckoutProjection;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckoutToDoList;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.ScheduledGroupCheckoutToDoList;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccount;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountEvent;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.InitiateGroupCheckout;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountsConfig.configureGuestStayAccounts;
import static org.assertj.core.api.Assertions.assertThat;

public class ScheduledBusinessProcessTests {

  private Database database;
  private MessageCatcher publishedMessages;
  private GuestStayAccountFacade guestStayFacade;
  private GroupCheckoutFacade groupCheckoutFacade;
  private ScheduledGroupCheckoutToDoList scheduledToDoList;
  private OffsetDateTime now;

  @BeforeEach
  public void setUp() {
    database = new Database();
    MessageBus messageBus = new InMemoryMessageBus();
    publishedMessages = new MessageCatcher();
    guestStayFacade = new GuestStayAccountFacade(database.collection(GuestStayAccount.class), messageBus);
    groupCheckoutFacade = new GroupCheckoutFacade(messageBus);
    now = OffsetDateTime.now();

    messageBus.use(publishedMessages::catchMessage);

    var groupCheckoutProjection = new GroupCheckoutProjection(database.collection(GroupCheckout.class));
    for (var eventType : groupCheckoutProjection.canHandle()) {
      messageBus.subscribe(eventType, groupCheckoutProjection::handle);
    }
    scheduledToDoList = new ScheduledGroupCheckoutToDoList(
      database.collection(GroupCheckout.class),
      new GroupCheckoutToDoList(groupCheckoutProjection, messageBus)
    );
    configureGuestStayAccounts(messageBus, guestStayFacade);
  }

  @Test
  public void groupCheckoutForMultipleGuestStay_ShouldCompleteOnceTimeHasPassed() {
    // Given
    UUID[] guestStays = new UUID[] { UUID.randomUUID(), UUID.randomUUID() };

    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[0], now.minusDays(1)));
    guestStayFacade.checkInGuest(new CheckInGuest(guestStays[1], now.minusDays(1)));
    publishedMessages.reset();
    // And
    var groupCheckoutId = UUID.randomUUID();
    var clerkId = UUID.randomUUID();

    // When
    groupCheckoutFacade.initiateGroupCheckout(new InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now));

    // Then
    publishedMessages.shouldReceiveMessages(List.of(
      new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now),
      new CheckOutGuest(guestStays[0], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[0], now, groupCheckoutId),
      new CheckOutGuest(guestStays[1], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[1], now, groupCheckoutId)
    ));

    // When
    scheduledToDoList.handleAll();
    scheduledToDoList.handleAll();

    // Then
    publishedMessages.shouldReceiveMessages(List.of(
      new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now),
      new CheckOutGuest(guestStays[0], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[0], now, groupCheckoutId),
      new CheckOutGuest(guestStays[1], now, groupCheckoutId),
      new GuestStayAccountEvent.GuestCheckedOut(guestStays[1], now, groupCheckoutId),
      new GroupCheckoutEvent.GroupCheckoutCompleted(groupCheckoutId, guestStays, now)
    ));
    assertThat(database.collection(GroupCheckout.class).get(groupCheckoutId))
      .as("done group checkout is removed from the to-do list")
      .isEmpty();
  }
}
