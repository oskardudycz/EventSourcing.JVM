package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.gueststayaccounts.GuestStayAccountEvent;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageCatcher;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        UUID[] guestStayAccountIds = new UUID[] {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        var command =
                new GroupCheckoutCommand.InitiateGroupCheckout(groupCheckoutId, clerkId, guestStayAccountIds, now);

        // When
        groupCheckoutFacade.initiateGroupCheckout(command);

        // Then
        publishedMessages.shouldReceiveMessages(List.of(
                new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStayAccountIds, now),
                new GuestStayAccountCommand.CheckOutGuest(guestStayAccountIds[0], now, groupCheckoutId),
                new GuestStayAccountCommand.CheckOutGuest(guestStayAccountIds[1], now, groupCheckoutId),
                new GuestStayAccountCommand.CheckOutGuest(guestStayAccountIds[2], now, groupCheckoutId)));
    }

    @Test
    public void recordingGuestCheckoutCompletion_Succeeds() {
        // Given
        var guestStayAccountId = UUID.randomUUID();
        var groupCheckoutId = initiateGroupCheckout(guestStayAccountId, UUID.randomUUID(), UUID.randomUUID());
        // And
        var event = new GuestStayAccountEvent.GuestCheckedOut(guestStayAccountId, now, groupCheckoutId);

        // When
        groupCheckoutFacade.recordGuestCheckoutCompletion(event);

        // Then
        publishedMessages.shouldReceiveMessages(List.of(
                new GroupCheckoutEvent.GuestCheckoutCompletionRecorded(groupCheckoutId, guestStayAccountId, now)));
    }

    @Test
    public void recordingGuestCheckoutFailure_Succeeds() {
        // Given
        var guestStayAccountId = UUID.randomUUID();
        var groupCheckoutId = initiateGroupCheckout(guestStayAccountId, UUID.randomUUID(), UUID.randomUUID());
        // And
        var event = new GuestStayAccountEvent.GuestCheckoutFailed(
                guestStayAccountId,
                GuestStayAccountEvent.GuestCheckoutFailed.FailureReason.BALANCE_NOT_SETTLED,
                now,
                groupCheckoutId);

        // When
        groupCheckoutFacade.recordGuestCheckoutFailure(event);

        // Then
        publishedMessages.shouldReceiveMessages(
                List.of(new GroupCheckoutEvent.GuestCheckoutFailureRecorded(groupCheckoutId, guestStayAccountId, now)));
    }

    @Test
    public void recordingLastGuestCheckoutCompletion_CompletesGroupCheckout() {
        // Given
        var completedGuestStayAccountId = UUID.randomUUID();
        var lastGuestStayAccountId = UUID.randomUUID();
        var groupCheckoutId = initiateGroupCheckout(completedGuestStayAccountId, lastGuestStayAccountId);
        groupCheckoutFacade.recordGuestCheckoutCompletion(
                new GuestStayAccountEvent.GuestCheckedOut(completedGuestStayAccountId, now, groupCheckoutId));
        publishedMessages.reset();
        // And
        var event = new GuestStayAccountEvent.GuestCheckedOut(lastGuestStayAccountId, now, groupCheckoutId);

        // When
        groupCheckoutFacade.recordGuestCheckoutCompletion(event);

        // Then
        publishedMessages.shouldReceiveMessages(List.of(
                new GroupCheckoutEvent.GuestCheckoutCompletionRecorded(groupCheckoutId, lastGuestStayAccountId, now),
                new GroupCheckoutEvent.GroupCheckoutCompleted(
                        groupCheckoutId, new UUID[] {completedGuestStayAccountId, lastGuestStayAccountId}, now)));
    }

    @Test
    public void recordingLastGuestCheckoutAfterFailure_FailsGroupCheckout() {
        // Given
        var failedGuestStayAccountId = UUID.randomUUID();
        var lastGuestStayAccountId = UUID.randomUUID();
        var groupCheckoutId = initiateGroupCheckout(failedGuestStayAccountId, lastGuestStayAccountId);
        groupCheckoutFacade.recordGuestCheckoutFailure(new GuestStayAccountEvent.GuestCheckoutFailed(
                failedGuestStayAccountId,
                GuestStayAccountEvent.GuestCheckoutFailed.FailureReason.BALANCE_NOT_SETTLED,
                now,
                groupCheckoutId));
        publishedMessages.reset();
        // And
        var event = new GuestStayAccountEvent.GuestCheckedOut(lastGuestStayAccountId, now, groupCheckoutId);

        // When
        groupCheckoutFacade.recordGuestCheckoutCompletion(event);

        // Then
        publishedMessages.shouldReceiveMessages(List.of(
                new GroupCheckoutEvent.GuestCheckoutCompletionRecorded(groupCheckoutId, lastGuestStayAccountId, now),
                new GroupCheckoutEvent.GroupCheckoutFailed(
                        groupCheckoutId,
                        new UUID[] {lastGuestStayAccountId},
                        new UUID[] {failedGuestStayAccountId},
                        now)));
    }

    private UUID initiateGroupCheckout(UUID... guestStayAccountIds) {
        var groupCheckoutId = UUID.randomUUID();
        groupCheckoutFacade.initiateGroupCheckout(new GroupCheckoutCommand.InitiateGroupCheckout(
                groupCheckoutId, UUID.randomUUID(), guestStayAccountIds, now.minusDays(1)));
        publishedMessages.reset();

        return groupCheckoutId;
    }
}
