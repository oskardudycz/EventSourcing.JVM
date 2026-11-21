package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class EntityDefinitionTests {
    @Test
    @Tag("Exercise")
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
                new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStayAccountIds, now)));
    }

    @Test
    @Tag("Exercise")
    public void recordingGuestCheckoutCompletion_Succeeds() {
        // Given
        var guestStayAccountId = UUID.randomUUID();
        var groupCheckoutId = initiateGroupCheckout(guestStayAccountId, UUID.randomUUID(), UUID.randomUUID());
        // And
        var command = new GroupCheckoutCommand.RecordGuestCheckoutCompletion(groupCheckoutId, guestStayAccountId, now);

        // When
        groupCheckoutFacade.recordGuestCheckoutCompletion(command);

        // Then
        publishedMessages.shouldReceiveMessages(List.of(
                new GroupCheckoutEvent.GuestCheckoutCompletionRecorded(groupCheckoutId, guestStayAccountId, now)));
    }

    @Test
    @Tag("Exercise")
    public void recordingGuestCheckoutFailure_Succeeds() {
        // Given
        var guestStayAccountId = UUID.randomUUID();
        var groupCheckoutId = initiateGroupCheckout(guestStayAccountId, UUID.randomUUID(), UUID.randomUUID());
        // And
        var command = new GroupCheckoutCommand.RecordGuestCheckoutFailure(groupCheckoutId, guestStayAccountId, now);

        // When
        groupCheckoutFacade.recordGuestCheckoutFailure(command);

        // Then
        publishedMessages.shouldReceiveMessages(
                List.of(new GroupCheckoutEvent.GuestCheckoutFailureRecorded(groupCheckoutId, guestStayAccountId, now)));
    }

    @Test
    @Tag("Exercise")
    public void recordingLastGuestCheckoutCompletion_CompletesGroupCheckout() {
        // Given
        var completedGuestStayAccountId = UUID.randomUUID();
        var lastGuestStayAccountId = UUID.randomUUID();
        var groupCheckoutId = initiateGroupCheckout(completedGuestStayAccountId, lastGuestStayAccountId);
        groupCheckoutFacade.recordGuestCheckoutCompletion(new GroupCheckoutCommand.RecordGuestCheckoutCompletion(
                groupCheckoutId, completedGuestStayAccountId, now));
        publishedMessages.reset();
        // And
        var command =
                new GroupCheckoutCommand.RecordGuestCheckoutCompletion(groupCheckoutId, lastGuestStayAccountId, now);

        // When
        groupCheckoutFacade.recordGuestCheckoutCompletion(command);

        // Then
        publishedMessages.shouldReceiveMessages(List.of(
                new GroupCheckoutEvent.GuestCheckoutCompletionRecorded(groupCheckoutId, lastGuestStayAccountId, now),
                new GroupCheckoutEvent.GroupCheckoutCompleted(
                        groupCheckoutId, new UUID[] {completedGuestStayAccountId, lastGuestStayAccountId}, now)));
    }

    @Test
    @Tag("Exercise")
    public void recordingLastGuestCheckoutAfterFailure_FailsGroupCheckout() {
        // Given
        var failedGuestStayAccountId = UUID.randomUUID();
        var lastGuestStayAccountId = UUID.randomUUID();
        var groupCheckoutId = initiateGroupCheckout(failedGuestStayAccountId, lastGuestStayAccountId);
        groupCheckoutFacade.recordGuestCheckoutFailure(
                new GroupCheckoutCommand.RecordGuestCheckoutFailure(groupCheckoutId, failedGuestStayAccountId, now));
        publishedMessages.reset();
        // And
        var command =
                new GroupCheckoutCommand.RecordGuestCheckoutCompletion(groupCheckoutId, lastGuestStayAccountId, now);

        // When
        groupCheckoutFacade.recordGuestCheckoutCompletion(command);

        // Then
        publishedMessages.shouldReceiveMessages(List.of(
                new GroupCheckoutEvent.GuestCheckoutCompletionRecorded(groupCheckoutId, lastGuestStayAccountId, now),
                new GroupCheckoutEvent.GroupCheckoutFailed(
                        groupCheckoutId,
                        new UUID[] {lastGuestStayAccountId},
                        new UUID[] {failedGuestStayAccountId},
                        now)));
    }

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

    private UUID initiateGroupCheckout(UUID... guestStayAccountIds) {
        var groupCheckoutId = UUID.randomUUID();
        groupCheckoutFacade.initiateGroupCheckout(new GroupCheckoutCommand.InitiateGroupCheckout(
                groupCheckoutId, UUID.randomUUID(), guestStayAccountIds, now.minusDays(1)));
        publishedMessages.reset();

        return groupCheckoutId;
    }
}
