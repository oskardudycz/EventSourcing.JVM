package io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates;

import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.BackgroundJobs;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.groupcheckouts.GroupCheckout;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.groupcheckouts.GroupCheckoutEvent.GroupCheckoutInitiated;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.groupcheckouts.GroupCheckoutEvent.GuestCheckoutCompletionRecorded;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.groupcheckouts.GroupCheckoutEvent.GuestCheckoutFailureRecorded;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.groupcheckouts.GroupCheckoutFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

public class IdempotencyTests {
  private BackgroundJobs backgroundJobs;
  private MessageCatcher publishedMessages;
  private GroupCheckoutFacade groupCheckoutFacade;
  private OffsetDateTime now;

  @BeforeEach
  public void setUp() {
    Database database = new Database();
    MessageBus messageBus = new InMemoryMessageBus();
    backgroundJobs = new BackgroundJobs();
    publishedMessages = new MessageCatcher();
    groupCheckoutFacade = new GroupCheckoutFacade(database, messageBus, backgroundJobs);
    now = OffsetDateTime.now();

    messageBus.use(publishedMessages::catchMessage);
  }

  @Test
  public void groupCheckoutIsInitiatedOnceForTheSameId() {
    // Given
    var groupCheckoutId = UUID.randomUUID();
    var clerkId = UUID.randomUUID();
    UUID[] guestStays = new UUID[]{UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
    var command = new InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now);
    // And
    groupCheckoutFacade.initiateGroupCheckout(command);

    // When
    groupCheckoutFacade.initiateGroupCheckout(command);

    // Then
    backgroundJobs.runUntilIdle(publishedMessages);
    publishedMessages.shouldReceiveSingleMessage(
      new GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now)
    );
  }

  @Test
  public void groupCheckoutRecordsOnceGuestCheckoutCompletion() {
    // Given
    var groupCheckoutId = UUID.randomUUID();
    var clerkId = UUID.randomUUID();
    UUID[] guestStays = new UUID[]{UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
    // And
    groupCheckoutFacade.initiateGroupCheckout(
      new InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now.minusDays(1))
    );
    backgroundJobs.runUntilIdle(publishedMessages);
    publishedMessages.reset();

    var command = new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[0], now);
    groupCheckoutFacade.recordGuestCheckoutCompletion(command);

    // When
    groupCheckoutFacade.recordGuestCheckoutCompletion(command);

    // Then
    backgroundJobs.runUntilIdle(publishedMessages);
    publishedMessages.shouldReceiveSingleMessage(
      new GuestCheckoutCompletionRecorded(groupCheckoutId, guestStays[0], now)
    );
  }

  @Test
  public void groupCheckoutRecordsOnceGuestCheckoutFailure() {
    // Given
    var groupCheckoutId = UUID.randomUUID();
    var clerkId = UUID.randomUUID();
    UUID[] guestStays = new UUID[]{UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
    // And
    groupCheckoutFacade.initiateGroupCheckout(
      new InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now.minusDays(1))
    );
    backgroundJobs.runUntilIdle(publishedMessages);
    publishedMessages.reset();

    var command = new RecordGuestCheckoutFailure(groupCheckoutId, guestStays[0], now);
    groupCheckoutFacade.recordGuestCheckoutFailure(command);

    // When
    groupCheckoutFacade.recordGuestCheckoutFailure(command);

    // Then
    backgroundJobs.runUntilIdle(publishedMessages);
    publishedMessages.shouldReceiveSingleMessage(
      new GuestCheckoutFailureRecorded(groupCheckoutId, guestStays[0], now)
    );
  }
}
