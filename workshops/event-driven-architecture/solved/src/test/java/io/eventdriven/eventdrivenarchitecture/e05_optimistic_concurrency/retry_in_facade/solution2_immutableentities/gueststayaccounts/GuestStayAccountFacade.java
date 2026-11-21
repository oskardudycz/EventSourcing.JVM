package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution2_immutableentities.gueststayaccounts;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.FunctionalTools.FoldLeft.reduce;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution2_immutableentities.gueststayaccounts.GuestStayAccount.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.decide;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Retry;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Versioned;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution2_immutableentities.gueststayaccounts.GuestStayAccount.Status;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class GuestStayAccountFacade {
    private final Database.Collection<GuestStayAccount> collection;
    private final MessageBus messageBus;

    public GuestStayAccountFacade(Database.Collection<GuestStayAccount> collection, MessageBus messageBus) {
        this.collection = collection;
        this.messageBus = messageBus;
    }

    public void checkInGuest(CheckInGuest command) {
        var events = handle(command.guestStayId(), account -> {
            if (account.status() != Status.NOT_EXISTING) return List.of();

            return decide(command, account);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void recordCharge(RecordCharge command) {
        var events = handle(command.guestStayId(), account -> {
            if (account.status() == Status.NOT_EXISTING) throw new IllegalStateException("Entity not found");

            return decide(command, account);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void recordPayment(RecordPayment command) {
        var events = handle(command.guestStayId(), account -> {
            if (account.status() == Status.NOT_EXISTING) throw new IllegalStateException("Entity not found");

            return decide(command, account);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void checkOutGuest(CheckOutGuest command) {
        var events = handle(command.guestStayId(), account -> {
            if (account.status() == Status.NOT_EXISTING) return List.of();

            return decide(command, account);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    private List<GuestStayAccountEvent> handle(
            UUID guestStayId, Function<GuestStayAccount, List<GuestStayAccountEvent>> handle) {
        return Retry.onConflict(() -> {
            var loaded = collection.get(guestStayId);
            var account = loaded.map(Versioned::document).orElse(INITIAL);

            var events = handle.apply(account);
            if (events.isEmpty()) return events;

            collection.store(
                    guestStayId,
                    reduce(events, account, GuestStayAccount::evolve),
                    loaded.map(Versioned::version).orElse(0));

            return events;
        });
    }
}
