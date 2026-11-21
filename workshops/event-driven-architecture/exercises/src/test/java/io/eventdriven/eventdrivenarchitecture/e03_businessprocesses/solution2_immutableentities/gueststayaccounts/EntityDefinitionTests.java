package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution2_immutableentities.gueststayaccounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand;
import java.time.OffsetDateTime;
import java.util.UUID;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class EntityDefinitionTests {

    @Test
    @Tag("Exercise")
    public void recordingChargeForMissingGuestStayAccount_FailsWithEntityNotFound() {
        // Given
        var command = new GuestStayAccountCommand.RecordCharge(
                UUID.randomUUID(), faker.number().randomDouble(2, 10, 1000), now);
        publishedMessages.reset();

        // When
        var exception = assertThrows(IllegalStateException.class, () -> guestStayFacade.recordCharge(command));

        // Then
        assertEquals("Entity not found", exception.getMessage());
        publishedMessages.shouldNotReceiveAnyMessage();
    }

    @Test
    @Tag("Exercise")
    public void recordingPaymentForMissingGuestStayAccount_FailsWithEntityNotFound() {
        // Given
        var command = new GuestStayAccountCommand.RecordPayment(
                UUID.randomUUID(), faker.number().randomDouble(2, 10, 1000), now);
        publishedMessages.reset();

        // When
        var exception = assertThrows(IllegalStateException.class, () -> guestStayFacade.recordPayment(command));

        // Then
        assertEquals("Entity not found", exception.getMessage());
        publishedMessages.shouldNotReceiveAnyMessage();
    }

    private Database database;
    private MessageBus messageBus;
    private MessageCatcher publishedMessages;
    private GuestStayAccountFacade guestStayFacade;
    private Faker faker;
    private OffsetDateTime now;

    @BeforeEach
    public void setUp() {
        database = new Database();
        messageBus = new InMemoryMessageBus();
        publishedMessages = new MessageCatcher();
        guestStayFacade = new GuestStayAccountFacade(database, messageBus);
        faker = new Faker();
        now = OffsetDateTime.now();
        messageBus.use(publishedMessages::catchMessage);
    }

    @Test
    @Tag("Exercise")
    public void checkingInGuest_Succeeds() {
        // Given
        var guestStayAccountId = UUID.randomUUID();
        var command = new GuestStayAccountCommand.CheckInGuest(guestStayAccountId, now);
        publishedMessages.reset();

        // When
        guestStayFacade.checkInGuest(command);

        // Then
        publishedMessages.shouldReceiveSingleMessage(new GuestStayAccountEvent.GuestCheckedIn(guestStayAccountId, now));
    }

    @Test
    @Tag("Exercise")
    public void recordingChargeForCheckedInGuest_Succeeds() {
        // Given
        var guestStayAccountId = UUID.randomUUID();
        guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(guestStayAccountId, now.minusDays(1)));
        publishedMessages.reset();
        // And
        var amount = faker.number().randomDouble(2, 10, 1000);
        var command = new GuestStayAccountCommand.RecordCharge(guestStayAccountId, amount, now);

        // When
        guestStayFacade.recordCharge(command);

        // Then
        publishedMessages.shouldReceiveSingleMessage(
                new GuestStayAccountEvent.ChargeRecorded(guestStayAccountId, amount, now));
    }

    @Test
    @Tag("Exercise")
    public void recordingPaymentForCheckedInGuest_Succeeds() {
        // Given
        var guestStayAccountId = UUID.randomUUID();
        guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(guestStayAccountId, now.minusDays(1)));
        publishedMessages.reset();
        // And
        var amount = faker.number().randomDouble(2, 10, 1000);
        var command = new GuestStayAccountCommand.RecordPayment(guestStayAccountId, amount, now);

        // When
        guestStayFacade.recordPayment(command);

        // Then
        publishedMessages.shouldReceiveSingleMessage(
                new GuestStayAccountEvent.PaymentRecorded(guestStayAccountId, amount, now));
    }

    @Test
    @Tag("Exercise")
    public void recordingPaymentForCheckedInGuestWithCharge_Succeeds() {
        // Given
        var guestStayAccountId = UUID.randomUUID();
        guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(guestStayAccountId, now.minusDays(1)));
        guestStayFacade.recordCharge(new GuestStayAccountCommand.RecordCharge(
                guestStayAccountId, faker.number().randomDouble(2, 10, 1000), now.minusHours(1)));
        publishedMessages.reset();
        // And
        var amount = faker.number().randomDouble(2, 10, 1000);
        var command = new GuestStayAccountCommand.RecordPayment(guestStayAccountId, amount, now);

        // When
        guestStayFacade.recordPayment(command);

        // Then
        publishedMessages.shouldReceiveSingleMessage(
                new GuestStayAccountEvent.PaymentRecorded(guestStayAccountId, amount, now));
    }

    @Test
    @Tag("Exercise")
    public void checkingOutGuestWithSettledBalance_Succeeds() {
        // Given
        var guestStayAccountId = UUID.randomUUID();

        var amount = faker.number().randomDouble(2, 10, 1000);
        guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(guestStayAccountId, now.minusDays(1)));
        guestStayFacade.recordCharge(
                new GuestStayAccountCommand.RecordCharge(guestStayAccountId, amount, now.minusHours(2)));
        guestStayFacade.recordPayment(
                new GuestStayAccountCommand.RecordPayment(guestStayAccountId, amount, now.minusHours(1)));
        publishedMessages.reset();
        // And
        var command = new GuestStayAccountCommand.CheckOutGuest(guestStayAccountId, now);

        // When
        guestStayFacade.checkOutGuest(command);

        // Then
        publishedMessages.shouldReceiveSingleMessage(
                new GuestStayAccountEvent.GuestCheckedOut(guestStayAccountId, now, null));
    }

    @Test
    @Tag("Exercise")
    public void checkingOutGuestWithUnsettledBalance_FailsWithGuestCheckoutFailed() {
        // Given
        var guestStayAccountId = UUID.randomUUID();

        var amount = faker.number().randomDouble(2, 10, 1000);
        guestStayFacade.checkInGuest(new GuestStayAccountCommand.CheckInGuest(guestStayAccountId, now.minusDays(1)));
        guestStayFacade.recordCharge(
                new GuestStayAccountCommand.RecordCharge(guestStayAccountId, amount + 10, now.minusHours(2)));
        guestStayFacade.recordPayment(
                new GuestStayAccountCommand.RecordPayment(guestStayAccountId, amount, now.minusHours(1)));
        publishedMessages.reset();
        // And
        var command = new GuestStayAccountCommand.CheckOutGuest(guestStayAccountId, now);

        // When
        try {
            guestStayFacade.checkOutGuest(command);
        } catch (Exception exc) {
            System.out.println(exc.getMessage());
        }

        // Then
        publishedMessages.shouldReceiveSingleMessage(new GuestStayAccountEvent.GuestCheckoutFailed(
                guestStayAccountId,
                GuestStayAccountEvent.GuestCheckoutFailed.FailureReason.BALANCE_NOT_SETTLED,
                now,
                null));
    }
}
