package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution2_immutableentities.gueststayaccounts;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.ChargeRecorded;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.GuestCheckedIn;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.GuestCheckedOut;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.PaymentRecorded;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IdempotencyTests {
    @Test
    public void guestIsCheckedInOnceForTheSameGuestStayAccount() {
        // Given
        var guestStayId = UUID.randomUUID();
        var command = new CheckInGuest(guestStayId, now);
        // And
        guestStayFacade.checkInGuest(command);

        // When
        guestStayFacade.checkInGuest(command);

        // Then
        publishedMessages.shouldReceiveSingleMessage(new GuestCheckedIn(guestStayId, now));
    }

    @Test
    public void chargeIsRecordedOnceForTheSameChargeId() {
        // Given
        var guestStayId = checkInGuest();
        var chargeId = UUID.randomUUID();
        var command = new RecordCharge(guestStayId, chargeId, 100, now);
        // And
        guestStayFacade.recordCharge(command);

        // When
        guestStayFacade.recordCharge(command);

        // Then
        publishedMessages.shouldReceiveSingleMessage(new ChargeRecorded(guestStayId, chargeId, 100, now));
    }

    @Test
    public void paymentIsRecordedOnceForTheSamePaymentId() {
        // Given
        var guestStayId = checkInGuest();
        var paymentId = UUID.randomUUID();
        var command = new RecordPayment(guestStayId, paymentId, 100, now);
        // And
        guestStayFacade.recordPayment(command);

        // When
        guestStayFacade.recordPayment(command);

        // Then
        publishedMessages.shouldReceiveSingleMessage(new PaymentRecorded(guestStayId, paymentId, 100, now));
    }

    @Test
    public void guestIsCheckedOutOnceForTheSameGroupCheckout() {
        // Given
        var guestStayId = checkInGuest();
        var groupCheckoutId = UUID.randomUUID();
        var command = new CheckOutGuest(guestStayId, now, groupCheckoutId);
        // And
        guestStayFacade.checkOutGuest(command);

        // When
        guestStayFacade.checkOutGuest(command);

        // Then
        publishedMessages.shouldReceiveSingleMessage(new GuestCheckedOut(guestStayId, now, groupCheckoutId));
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
        var guestStayId = UUID.randomUUID();
        guestStayFacade.checkInGuest(new CheckInGuest(guestStayId, now));
        publishedMessages.reset();
        return guestStayId;
    }
}
