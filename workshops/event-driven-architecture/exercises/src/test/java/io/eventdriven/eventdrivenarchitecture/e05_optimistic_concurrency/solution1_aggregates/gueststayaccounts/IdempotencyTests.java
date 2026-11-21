package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.gueststayaccounts;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade.GuestStayAccountCommand.*;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.ChargeRecorded;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.GuestCheckedIn;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.GuestCheckedOut;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.PaymentRecorded;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class IdempotencyTests {
    @Test
    @Tag("Exercise")
    public void guestIsCheckedInOnceForTheSameGuestStayAccount() {
        // Given
        var guestStayAccountId = UUID.randomUUID();
        var command = new CheckInGuest(guestStayAccountId, now);
        // And
        guestStayFacade.checkInGuest(command);

        // When
        guestStayFacade.checkInGuest(command);

        // Then
        publishedMessages.shouldReceiveSingleMessage(new GuestCheckedIn(guestStayAccountId, now));
    }

    @Test
    @Tag("Exercise")
    public void chargeIsRecordedOnceForTheSameChargeId() {
        // Given
        var guestStayAccountId = checkInGuest();
        var chargeId = UUID.randomUUID();
        var command = new RecordCharge(guestStayAccountId, chargeId, 100, now);
        // And
        guestStayFacade.recordCharge(command);

        // When
        guestStayFacade.recordCharge(command);

        // Then
        publishedMessages.shouldReceiveSingleMessage(new ChargeRecorded(guestStayAccountId, chargeId, 100, now));
    }

    @Test
    @Tag("Exercise")
    public void paymentIsRecordedOnceForTheSamePaymentId() {
        // Given
        var guestStayAccountId = checkInGuest();
        var paymentId = UUID.randomUUID();
        var command = new RecordPayment(guestStayAccountId, paymentId, 100, now);
        // And
        guestStayFacade.recordPayment(command);

        // When
        guestStayFacade.recordPayment(command);

        // Then
        publishedMessages.shouldReceiveSingleMessage(new PaymentRecorded(guestStayAccountId, paymentId, 100, now));
    }

    @Test
    @Tag("Exercise")
    public void guestIsCheckedOutOnceForTheSameGroupCheckout() {
        // Given
        var guestStayAccountId = checkInGuest();
        var groupCheckoutId = UUID.randomUUID();
        var command = new CheckOutGuest(guestStayAccountId, now, groupCheckoutId);
        // And
        guestStayFacade.checkOutGuest(command);

        // When
        guestStayFacade.checkOutGuest(command);

        // Then
        publishedMessages.shouldReceiveSingleMessage(new GuestCheckedOut(guestStayAccountId, now, groupCheckoutId));
    }

    private MessageCatcher publishedMessages;
    private GuestStayAccountFacade guestStayFacade;
    private OffsetDateTime now;

    @BeforeEach
    public void setUp() {
        Database database = new Database();
        MessageBus messageBus = new InMemoryMessageBus();
        publishedMessages = new MessageCatcher();
        guestStayFacade = new GuestStayAccountFacade(database.collection(GuestStayAccount.class), messageBus);
        now = OffsetDateTime.now();

        messageBus.use(publishedMessages::catchMessage);
    }

    private UUID checkInGuest() {
        var guestStayAccountId = UUID.randomUUID();
        guestStayFacade.checkInGuest(new CheckInGuest(guestStayAccountId, now));
        publishedMessages.reset();
        return guestStayAccountId;
    }
}
