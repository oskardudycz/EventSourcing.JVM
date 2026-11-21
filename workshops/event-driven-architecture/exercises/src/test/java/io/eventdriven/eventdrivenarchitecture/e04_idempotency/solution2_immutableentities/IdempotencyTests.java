package io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities;

import static io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.groupcheckouts.GroupCheckoutsConfig.configureGroupCheckouts;
import static io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountsConfig.configureGuestStayAccounts;

import io.eventdriven.eventdrivenarchitecture.e04_idempotency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.groupcheckouts.GroupCheckout;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.groupcheckouts.GroupCheckoutEvent.GroupCheckoutCompleted;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.groupcheckouts.GroupCheckoutEvent.GroupCheckoutInitiated;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.groupcheckouts.GroupCheckoutEvent.GuestCheckoutCompletionRecorded;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.groupcheckouts.GroupCheckoutFacade;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccount;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.GuestCheckedOut;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountFacade;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class IdempotencyTests {
    @Test
    @Tag("Exercise")
    public void groupCheckoutCompletesOnceWhenTheBusDeliversEveryMessageTwice() {
        // Given
        var database = new Database();
        var messageBus = new InMemoryMessageBus(true);
        var publishedMessages = new MessageCatcher();
        messageBus.use(publishedMessages::catchMessage);

        var guestStayFacade = new GuestStayAccountFacade(database.collection(GuestStayAccount.class), messageBus);
        var groupCheckoutFacade = new GroupCheckoutFacade(database.collection(GroupCheckout.class), messageBus);
        configureGroupCheckouts(messageBus, groupCheckoutFacade);
        configureGuestStayAccounts(messageBus, guestStayFacade);

        UUID[] guestStays = new UUID[] {UUID.randomUUID(), UUID.randomUUID()};
        for (var guestStayAccountId : guestStays)
            guestStayFacade.checkInGuest(new CheckInGuest(guestStayAccountId, now));
        publishedMessages.reset();
        // And
        var groupCheckoutId = UUID.randomUUID();
        var clerkId = UUID.randomUUID();

        // When
        groupCheckoutFacade.initiateGroupCheckout(new InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now));

        // Then
        publishedMessages.shouldReceiveMessages(List.of(
                new GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now),
                new CheckOutGuest(guestStays[0], now, groupCheckoutId),
                new GuestCheckedOut(guestStays[0], now, groupCheckoutId),
                new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[0], now),
                new GuestCheckoutCompletionRecorded(groupCheckoutId, guestStays[0], now),
                new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[0], now),
                new CheckOutGuest(guestStays[1], now, groupCheckoutId),
                new GuestCheckedOut(guestStays[1], now, groupCheckoutId),
                new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[1], now),
                new GuestCheckoutCompletionRecorded(groupCheckoutId, guestStays[1], now),
                new GroupCheckoutCompleted(groupCheckoutId, guestStays, now),
                new RecordGuestCheckoutCompletion(groupCheckoutId, guestStays[1], now),
                new CheckOutGuest(guestStays[0], now, groupCheckoutId),
                new CheckOutGuest(guestStays[1], now, groupCheckoutId)));
    }

    private OffsetDateTime now;

    @BeforeEach
    public void setUp() {
        now = OffsetDateTime.now();
    }
}
