package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.FunctionalTools.FoldLeft.reduce;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts.GuestStayAccount.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.decide;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Versioned;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;
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
        if (collection.get(command.guestStayAccountId()).isPresent()) return;

        var checkedIn = decide(command, INITIAL);

        collection.store(command.guestStayAccountId(), reduce(checkedIn, INITIAL, GuestStayAccount::evolve));
        messageBus.publish(checkedIn);
    }

    public void recordCharge(RecordCharge command) {
        handle(command.guestStayAccountId(), account -> decide(command, account));
    }

    public void recordPayment(RecordPayment command) {
        handle(command.guestStayAccountId(), account -> decide(command, account));
    }

    public void checkOutGuest(CheckOutGuest command) {
        var account = collection
                .get(command.guestStayAccountId())
                .map(Versioned::document)
                .orElse(null);
        if (account == null) return;

        var events = decide(command, account);

        collection.store(command.guestStayAccountId(), reduce(events, account, GuestStayAccount::evolve));
        messageBus.publish(events);
    }

    private void handle(UUID guestStayAccountId, Function<GuestStayAccount, List<GuestStayAccountEvent>> decide) {
        var account = collection
                .get(guestStayAccountId)
                .map(Versioned::document)
                .orElseThrow(() -> new IllegalStateException("Entity not found"));

        var events = decide.apply(account);

        collection.store(guestStayAccountId, reduce(events, account, GuestStayAccount::evolve));
        messageBus.publish(events);
    }
}
