package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution1_aggregates.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.core.AggregateStore;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public class GuestStayAccountFacade {
    private final AggregateStore<GuestStayAccount> store;
    private final MessageBus messageBus;

    public GuestStayAccountFacade(Database.Collection<GuestStayAccount> collection, MessageBus messageBus) {
        this.store = new AggregateStore<>(collection);
        this.messageBus = messageBus;
    }

    public void checkInGuest(GuestStayAccountCommand.CheckInGuest command) {
        var events = store.handle(command.guestStayId(), account -> {
            if (account.isPresent()) return Optional.empty();

            return Optional.of(GuestStayAccount.checkIn(command.guestStayId(), command.now()));
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void recordCharge(GuestStayAccountCommand.RecordCharge command) {
        var events = store.handle(command.guestStayId(), account -> {
            var existing = account.orElseThrow(() -> new IllegalStateException("Entity not found"));

            existing.recordCharge(command.chargeId(), command.amount(), command.now());

            return Optional.of(existing);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void recordPayment(GuestStayAccountCommand.RecordPayment command) {
        var events = store.handle(command.guestStayId(), account -> {
            var existing = account.orElseThrow(() -> new IllegalStateException("Entity not found"));

            existing.recordPayment(command.paymentId(), command.amount(), command.now());

            return Optional.of(existing);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void checkOutGuest(GuestStayAccountCommand.CheckOutGuest command) {
        var events = store.handle(
                command.guestStayId(),
                account -> account.map(existing -> {
                    existing.checkout(command.now(), command.groupCheckOutId());

                    return existing;
                }));

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public sealed interface GuestStayAccountCommand {
        record CheckInGuest(UUID guestStayId, OffsetDateTime now) implements GuestStayAccountCommand {}

        record RecordCharge(UUID guestStayId, UUID chargeId, double amount, OffsetDateTime now)
                implements GuestStayAccountCommand {}

        record RecordPayment(UUID guestStayId, UUID paymentId, double amount, OffsetDateTime now)
                implements GuestStayAccountCommand {}

        record CheckOutGuest(UUID guestStayId, OffsetDateTime now, UUID groupCheckOutId)
                implements GuestStayAccountCommand {
            public CheckOutGuest(UUID guestStayId, OffsetDateTime now) {
                this(guestStayId, now, null);
            }
        }
    }
}
