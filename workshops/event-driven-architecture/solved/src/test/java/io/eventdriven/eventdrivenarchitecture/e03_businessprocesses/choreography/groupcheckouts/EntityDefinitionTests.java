package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.gueststayaccounts.GuestStayAccountEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class EntityDefinitionTests {

  private Database database;
  private MessageBus messageBus;
  private MessageCatcher publishedMessages;
  private GroupCheckoutFacade groupCheckoutFacade;
  private OffsetDateTime now;

  @BeforeEach
  public void setUp() {
    database = new Database();
    messageBus = new InMemoryMessageBus();
    publishedMessages = new MessageCatcher();
    groupCheckoutFacade = new GroupCheckoutFacade(database.collection(GroupCheckout.class), messageBus);
    now = OffsetDateTime.now();
    messageBus.use(publishedMessages::catchMessage);
  }

  @Test
  public void initiatingGroupCheckout_Succeeds() {
    // Given
    var groupCheckoutId = UUID.randomUUID();
    var clerkId = UUID.randomUUID();
    UUID[] guestStayIds = new UUID[]{UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
    var command = new GroupCheckoutCommand.InitiateGroupCheckout(groupCheckoutId, clerkId, guestStayIds, now);

    // When
    groupCheckoutFacade.initiateGroupCheckout(command);

    // Then
    publishedMessages.shouldReceiveMessages(List.of(
      new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStayIds, now),
      new GuestStayAccountCommand.CheckOutGuest(guestStayIds[0], now, groupCheckoutId),
      new GuestStayAccountCommand.CheckOutGuest(guestStayIds[1], now, groupCheckoutId),
      new GuestStayAccountCommand.CheckOutGuest(guestStayIds[2], now, groupCheckoutId)
    ));
  }

  @Test
  public void recordingGuestCheckoutCompletion_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();
    var groupCheckoutId = initiateGroupCheckout(guestStayId, UUID.randomUUID(), UUID.randomUUID());
    // And
    var event = new GuestStayAccountEvent.GuestCheckedOut(guestStayId, now, groupCheckoutId);

    // When
    groupCheckoutFacade.recordGuestCheckoutCompletion(event);

    // Then
    publishedMessages.shouldReceiveMessages(List.of(
      new GroupCheckoutEvent.GuestCheckoutCompletionRecorded(groupCheckoutId, guestStayId, now)
    ));
  }

  @Test
  public void recordingGuestCheckoutFailure_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();
    var groupCheckoutId = initiateGroupCheckout(guestStayId, UUID.randomUUID(), UUID.randomUUID());
    // And
    var event = new GuestStayAccountEvent.GuestCheckoutFailed(guestStayId, GuestStayAccountEvent.GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED, now, groupCheckoutId);

    // When
    groupCheckoutFacade.recordGuestCheckoutFailure(event);

    // Then
    publishedMessages.shouldReceiveMessages(List.of(
      new GroupCheckoutEvent.GuestCheckoutFailureRecorded(groupCheckoutId, guestStayId, now)
    ));
  }

  @Test
  public void recordingLastGuestCheckoutCompletion_CompletesGroupCheckout() {
    // Given
    var completedGuestStayId = UUID.randomUUID();
    var lastGuestStayId = UUID.randomUUID();
    var groupCheckoutId = initiateGroupCheckout(completedGuestStayId, lastGuestStayId);
    groupCheckoutFacade.recordGuestCheckoutCompletion(
      new GuestStayAccountEvent.GuestCheckedOut(completedGuestStayId, now, groupCheckoutId)
    );
    publishedMessages.reset();
    // And
    var event = new GuestStayAccountEvent.GuestCheckedOut(lastGuestStayId, now, groupCheckoutId);

    // When
    groupCheckoutFacade.recordGuestCheckoutCompletion(event);

    // Then
    publishedMessages.shouldReceiveMessages(List.of(
      new GroupCheckoutEvent.GuestCheckoutCompletionRecorded(groupCheckoutId, lastGuestStayId, now),
      new GroupCheckoutEvent.GroupCheckoutCompleted(
        groupCheckoutId,
        new UUID[]{completedGuestStayId, lastGuestStayId},
        now
      )
    ));
  }

  @Test
  public void recordingLastGuestCheckoutAfterFailure_FailsGroupCheckout() {
    // Given
    var failedGuestStayId = UUID.randomUUID();
    var lastGuestStayId = UUID.randomUUID();
    var groupCheckoutId = initiateGroupCheckout(failedGuestStayId, lastGuestStayId);
    groupCheckoutFacade.recordGuestCheckoutFailure(
      new GuestStayAccountEvent.GuestCheckoutFailed(failedGuestStayId, GuestStayAccountEvent.GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED, now, groupCheckoutId)
    );
    publishedMessages.reset();
    // And
    var event = new GuestStayAccountEvent.GuestCheckedOut(lastGuestStayId, now, groupCheckoutId);

    // When
    groupCheckoutFacade.recordGuestCheckoutCompletion(event);

    // Then
    publishedMessages.shouldReceiveMessages(List.of(
      new GroupCheckoutEvent.GuestCheckoutCompletionRecorded(groupCheckoutId, lastGuestStayId, now),
      new GroupCheckoutEvent.GroupCheckoutFailed(
        groupCheckoutId,
        new UUID[]{lastGuestStayId},
        new UUID[]{failedGuestStayId},
        now
      )
    ));
  }

  private UUID initiateGroupCheckout(UUID... guestStayIds) {
    var groupCheckoutId = UUID.randomUUID();
    groupCheckoutFacade.initiateGroupCheckout(
      new GroupCheckoutCommand.InitiateGroupCheckout(groupCheckoutId, UUID.randomUUID(), guestStayIds, now.minusDays(1))
    );
    publishedMessages.reset();

    return groupCheckoutId;
  }
}
