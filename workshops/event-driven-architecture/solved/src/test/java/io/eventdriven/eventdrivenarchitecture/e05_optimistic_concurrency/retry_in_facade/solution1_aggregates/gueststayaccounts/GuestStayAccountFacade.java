package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution1_aggregates.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Retry;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Versioned;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class GuestStayAccountFacade {
    private final Database.Collection<GuestStayAccount> collection;
    private final MessageBus messageBus;

    public GuestStayAccountFacade(Database.Collection<GuestStayAccount> collection, MessageBus messageBus) {
        this.collection = collection;
        this.messageBus = messageBus;
    }

    public void checkInGuest(GuestStayAccountCommand.CheckInGuest command) {
        var events = handle(command.guestStayAccountId(), account -> {
            if (account.isPresent()) return Optional.empty();

            return Optional.of(GuestStayAccount.checkIn(command.guestStayAccountId(), command.now()));
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void recordCharge(GuestStayAccountCommand.RecordCharge command) {
        var events = handle(command.guestStayAccountId(), account -> {
            var existing = account.orElseThrow(() -> new IllegalStateException("Entity not found"));

            existing.recordCharge(command.chargeId(), command.amount(), command.now());

            return Optional.of(existing);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void recordPayment(GuestStayAccountCommand.RecordPayment command) {
        var events = handle(command.guestStayAccountId(), account -> {
            var existing = account.orElseThrow(() -> new IllegalStateException("Entity not found"));

            existing.recordPayment(command.paymentId(), command.amount(), command.now());

            return Optional.of(existing);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void checkOutGuest(GuestStayAccountCommand.CheckOutGuest command) {
        var events = handle(
                command.guestStayAccountId(),
                account -> account.map(existing -> {
                    existing.checkout(command.now(), command.groupCheckoutId());

                    return existing;
                }));

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    private List<Object> handle(
            UUID guestStayAccountId, Function<Optional<GuestStayAccount>, Optional<GuestStayAccount>> handle) {
        return Retry.onConflict(() -> {
            var loaded = collection.get(guestStayAccountId);

            var account = handle.apply(loaded.map(Versioned::document));
            if (account.isEmpty()) return List.of();

            var events = account.get().dequeueUncommittedEvents();
            if (events.isEmpty()) return events;

            collection.store(
                    guestStayAccountId,
                    account.get(),
                    loaded.map(Versioned::version).orElse(0));

            return events;
        });
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
