package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.groupcheckouts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.InitiateGroupCheckout;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts.GuestStayAccountEvent;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts.GuestStayAccountFacade;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EntityDefinitionTests {

    @Test
    public void initiatingGroupCheckoutWithMissingGuestStayAccount_FailsWithEntityNotFound() {
        // Given
        var checkedInGuestStayId = UUID.randomUUID();
        var missingGuestStayId = UUID.randomUUID();
        guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(checkedInGuestStayId, now.minusDays(1)));
        publishedMessages.reset();
        // And
        var command = new InitiateGroupCheckout(
                UUID.randomUUID(), UUID.randomUUID(), new UUID[] {checkedInGuestStayId, missingGuestStayId}, now);

        // When
        var exception =
                assertThrows(IllegalStateException.class, () -> groupCheckoutFacade.initiateGroupCheckout(command));

        // Then
        assertEquals("Entity not found", exception.getMessage());
        publishedMessages.shouldNotReceiveAnyMessage();
    }

    @Test
    public void initiatingGroupCheckout_StoresEachGuestStayAccountUnderItsOwnId() {
        // Given
        var firstGuestStayId = UUID.randomUUID();
        var secondGuestStayId = UUID.randomUUID();
        guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(firstGuestStayId, now.minusDays(1)));
        guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(secondGuestStayId, now.minusDays(1)));
        // And
        var groupCheckoutId = UUID.randomUUID();
        groupCheckoutFacade.initiateGroupCheckout(new InitiateGroupCheckout(
                groupCheckoutId, UUID.randomUUID(), new UUID[] {firstGuestStayId, secondGuestStayId}, now));
        publishedMessages.reset();

        // When
        guestStayFacade.checkOutGuest(new GuestStayAccountCommand.CheckOutGuest(firstGuestStayId, now));

        // Then
        publishedMessages.shouldReceiveSingleMessage(new GuestStayAccountEvent.GuestCheckoutFailed(
                firstGuestStayId, GuestStayAccountEvent.GuestCheckoutFailed.Reason.INVALID_STATE, now, null));
    }

    private Database database;
    private MessageBus messageBus;
    private MessageCatcher publishedMessages;
    private GuestStayAccountFacade guestStayFacade;
    private GroupCheckoutFacade groupCheckoutFacade;
    private OffsetDateTime now;

    @BeforeEach
    public void setUp() {
        database = new Database();
        messageBus = new InMemoryMessageBus();
        publishedMessages = new MessageCatcher();
        guestStayFacade = new GuestStayAccountFacade(database, messageBus);
        groupCheckoutFacade = new GroupCheckoutFacade(database, messageBus);
        now = OffsetDateTime.now();
        messageBus.use(publishedMessages::catchMessage);
    }
}
