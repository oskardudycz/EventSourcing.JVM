package io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.BackgroundJobs;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Retry;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.Outbox;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;

public class GuestStayAccountFacade {
    private static final UUID OUTBOX_ID = UUID.fromString("723829b2-021a-40fd-b388-53af3c18a9c1");

    private final Database database;

    public GuestStayAccountFacade(Database database, MessageBus messageBus, BackgroundJobs backgroundJobs) {
        this.database = database;

        backgroundJobs.register(() -> Outbox.publishFromOutbox(database, messageBus, OUTBOX_ID));
    }

    public void checkInGuest(GuestStayAccountCommand.CheckInGuest command) {
        Retry.untilSucceeds(() -> database.transaction(transaction -> {
            var account = GuestStayAccount.checkIn(command.guestStayAccountId(), command.now());

            transaction.collection(GuestStayAccount.class).store(command.guestStayAccountId(), account, 0);

            Outbox.storeInOutbox(transaction, OUTBOX_ID, account.dequeueUncommittedEvents());
        }));
    }

    public void recordCharge(GuestStayAccountCommand.RecordCharge command) {
        handle(command.guestStayAccountId(), account -> account.recordCharge(command.amount(), command.now()));
    }

    public void recordPayment(GuestStayAccountCommand.RecordPayment command) {
        handle(command.guestStayAccountId(), account -> account.recordPayment(command.amount(), command.now()));
    }

    public void checkOutGuest(GuestStayAccountCommand.CheckOutGuest command) {
        handle(command.guestStayAccountId(), account -> account.checkout(command.now(), command.groupCheckoutId()));
    }

    private void handle(UUID guestStayAccountId, Consumer<GuestStayAccount> handle) {
        Retry.onConflict(() -> Retry.untilSucceeds(() -> database.transaction(transaction -> {
            var collection = transaction.collection(GuestStayAccount.class);
            var loaded =
                    collection.get(guestStayAccountId).orElseThrow(() -> new IllegalStateException("Entity not found"));
            var account = loaded.document();

            handle.accept(account);

            collection.store(guestStayAccountId, account, loaded.version());

            Outbox.storeInOutbox(transaction, OUTBOX_ID, account.dequeueUncommittedEvents());
        })));
    }

    public sealed interface GuestStayAccountCommand {
        record CheckInGuest(UUID guestStayAccountId, OffsetDateTime now) implements GuestStayAccountCommand {}

        record RecordCharge(UUID guestStayAccountId, double amount, OffsetDateTime now)
                implements GuestStayAccountCommand {}

        record RecordPayment(UUID guestStayAccountId, double amount, OffsetDateTime now)
                implements GuestStayAccountCommand {}

        record CheckOutGuest(UUID guestStayAccountId, OffsetDateTime now, UUID groupCheckoutId)
                implements GuestStayAccountCommand {
            public CheckOutGuest(UUID guestStayAccountId, OffsetDateTime now) {
                this(guestStayAccountId, now, null);
            }
        }
    }
}
