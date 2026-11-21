package io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.BackgroundJobs;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Retry;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;

public class GuestStayAccountFacade {
    private final Database database;
    private final Database.Collection<GuestStayAccount> collection;
    private final MessageBus messageBus;

    public GuestStayAccountFacade(Database database, MessageBus messageBus, BackgroundJobs backgroundJobs) {
        this.database = database;
        this.collection = database.collection(GuestStayAccount.class);
        this.messageBus = messageBus;
    }

    public void checkInGuest(GuestStayAccountCommand.CheckInGuest command) {
        var account = GuestStayAccount.checkIn(command.guestStayAccountId(), command.now());

        collection.store(command.guestStayAccountId(), account, 0);
        messageBus.publish(account.dequeueUncommittedEvents());
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
        var events = Retry.onConflict(() -> {
            var loaded =
                    collection.get(guestStayAccountId).orElseThrow(() -> new IllegalStateException("Entity not found"));
            var account = loaded.document();

            handle.accept(account);

            collection.store(guestStayAccountId, account, loaded.version());

            return account.dequeueUncommittedEvents();
        });

        messageBus.publish(events);
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
