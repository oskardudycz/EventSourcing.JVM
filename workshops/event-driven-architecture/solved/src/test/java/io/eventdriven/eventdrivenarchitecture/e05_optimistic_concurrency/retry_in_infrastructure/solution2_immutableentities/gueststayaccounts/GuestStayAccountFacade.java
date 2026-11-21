package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution2_immutableentities.gueststayaccounts;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution2_immutableentities.gueststayaccounts.GuestStayAccount.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.decide;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.core.CommandHandler;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution2_immutableentities.gueststayaccounts.GuestStayAccount.GuestStayAccountStatus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;
import java.util.List;

public class GuestStayAccountFacade {
    private final CommandHandler<GuestStayAccount, GuestStayAccountEvent> commandHandler;
    private final MessageBus messageBus;

    public GuestStayAccountFacade(Database.Collection<GuestStayAccount> collection, MessageBus messageBus) {
        this.commandHandler = new CommandHandler<>(collection, INITIAL, GuestStayAccount::evolve);
        this.messageBus = messageBus;
    }

    public void checkInGuest(CheckInGuest command) {
        var events = commandHandler.handle(command.guestStayAccountId(), account -> {
            if (account.status() != GuestStayAccountStatus.NOT_EXISTING) return List.of();

            return decide(command, account);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void recordCharge(RecordCharge command) {
        var events = commandHandler.handle(command.guestStayAccountId(), account -> {
            if (account.status() == GuestStayAccountStatus.NOT_EXISTING)
                throw new IllegalStateException("Entity not found");

            return decide(command, account);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void recordPayment(RecordPayment command) {
        var events = commandHandler.handle(command.guestStayAccountId(), account -> {
            if (account.status() == GuestStayAccountStatus.NOT_EXISTING)
                throw new IllegalStateException("Entity not found");

            return decide(command, account);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void checkOutGuest(CheckOutGuest command) {
        var events = commandHandler.handle(command.guestStayAccountId(), account -> {
            if (account.status() == GuestStayAccountStatus.NOT_EXISTING) return List.of();

            return decide(command, account);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }
}
