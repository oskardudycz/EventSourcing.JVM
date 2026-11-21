package io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution1_aggregates.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e04_idempotency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.core.MessageBus;
import java.time.OffsetDateTime;
import java.util.UUID;

public class GuestStayAccountFacade {
    private final Database.Collection<GuestStayAccount> collection;
    private final MessageBus messageBus;

    public GuestStayAccountFacade(Database.Collection<GuestStayAccount> collection, MessageBus messageBus) {
        this.collection = collection;
        this.messageBus = messageBus;
    }

    public void checkInGuest(GuestStayAccountCommand.CheckInGuest command) {
        if (collection.get(command.guestStayAccountId()).isPresent()) return;

        var account = GuestStayAccount.checkIn(command.guestStayAccountId(), command.now());

        collection.store(command.guestStayAccountId(), account);
        messageBus.publish(account.dequeueUncommittedEvents());
    }

    public void recordCharge(GuestStayAccountCommand.RecordCharge command) {
        var account = collection
                .get(command.guestStayAccountId())
                .orElseThrow(() -> new IllegalStateException("Entity not found"));

        account.recordCharge(command.chargeId(), command.amount(), command.now());

        collection.store(command.guestStayAccountId(), account);
        messageBus.publish(account.dequeueUncommittedEvents());
    }

    public void recordPayment(GuestStayAccountCommand.RecordPayment command) {
        var account = collection
                .get(command.guestStayAccountId())
                .orElseThrow(() -> new IllegalStateException("Entity not found"));

        account.recordPayment(command.paymentId(), command.amount(), command.now());

        collection.store(command.guestStayAccountId(), account);
        messageBus.publish(account.dequeueUncommittedEvents());
    }

    public void checkOutGuest(GuestStayAccountCommand.CheckOutGuest command) {
        var account = collection.get(command.guestStayAccountId()).orElse(null);
        if (account == null) return;

        account.checkout(command.now(), command.groupCheckoutId());

        collection.store(command.guestStayAccountId(), account);
        messageBus.publish(account.dequeueUncommittedEvents());
    }

    public sealed interface GuestStayAccountCommand {
        record CheckInGuest(UUID guestStayAccountId, OffsetDateTime now) implements GuestStayAccountCommand {}

        record RecordCharge(UUID guestStayAccountId, UUID chargeId, double amount, OffsetDateTime now)
                implements GuestStayAccountCommand {}

        record RecordPayment(UUID guestStayAccountId, UUID paymentId, double amount, OffsetDateTime now)
                implements GuestStayAccountCommand {}

        record CheckOutGuest(UUID guestStayAccountId, OffsetDateTime now, UUID groupCheckoutId)
                implements GuestStayAccountCommand {
            public CheckOutGuest(UUID guestStayAccountId, OffsetDateTime now) {
                this(guestStayAccountId, now, null);
            }
        }
    }
}
