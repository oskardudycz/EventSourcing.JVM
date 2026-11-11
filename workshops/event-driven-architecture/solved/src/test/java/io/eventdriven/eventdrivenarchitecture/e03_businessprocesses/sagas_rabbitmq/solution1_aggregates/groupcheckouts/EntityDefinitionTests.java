package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand;
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
    groupCheckoutFacade = new GroupCheckoutFacade(database, messageBus);
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
      new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStayIds, now)
    ));
  }

  @Test
  public void recordingGuestCheckoutCompletion_Succeeds() {
    // Given
    var guestStayId = UUID.randomUUID();
    var groupCheckoutId = initiateGroupCheckout(guestStayId, UUID.randomUUID(), UUID.randomUUID());
    // And
    var command = new GroupCheckoutCommand.RecordGuestCheckoutCompletion(groupCheckoutId, guestStayId, now);

    // When
    groupCheckoutFacade.recordGuestCheckoutCompletion(command);

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
    var command = new GroupCheckoutCommand.RecordGuestCheckoutFailure(groupCheckoutId, guestStayId, now);

    // When
    groupCheckoutFacade.recordGuestCheckoutFailure(command);

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
      new GroupCheckoutCommand.RecordGuestCheckoutCompletion(groupCheckoutId, completedGuestStayId, now)
    );
    publishedMessages.reset();
    // And
    var command = new GroupCheckoutCommand.RecordGuestCheckoutCompletion(groupCheckoutId, lastGuestStayId, now);

    // When
    groupCheckoutFacade.recordGuestCheckoutCompletion(command);

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
      new GroupCheckoutCommand.RecordGuestCheckoutFailure(groupCheckoutId, failedGuestStayId, now)
    );
    publishedMessages.reset();
    // And
    var command = new GroupCheckoutCommand.RecordGuestCheckoutCompletion(groupCheckoutId, lastGuestStayId, now);

    // When
    groupCheckoutFacade.recordGuestCheckoutCompletion(command);

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
