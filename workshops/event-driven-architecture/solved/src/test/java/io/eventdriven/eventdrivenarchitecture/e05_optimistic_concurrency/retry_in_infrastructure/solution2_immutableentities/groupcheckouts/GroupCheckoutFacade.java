package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution2_immutableentities.groupcheckouts;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution2_immutableentities.groupcheckouts.GroupCheckout.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.decide;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.core.CommandHandler;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution2_immutableentities.groupcheckouts.GroupCheckout.CheckoutStatus;
import java.util.List;

public class GroupCheckoutFacade {
    private final CommandHandler<GroupCheckout, GroupCheckoutEvent> commandHandler;
    private final MessageBus messageBus;

    public GroupCheckoutFacade(Database.Collection<GroupCheckout> collection, MessageBus messageBus) {
        this.commandHandler = new CommandHandler<>(collection, INITIAL, GroupCheckout::evolve);
        this.messageBus = messageBus;
    }

    public void initiateGroupCheckout(InitiateGroupCheckout command) {
        var events = commandHandler.handle(command.groupCheckoutId(), groupCheckout -> {
            if (groupCheckout.status() != CheckoutStatus.NOT_EXISTING) return List.of();

            return decide(command, groupCheckout);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void recordGuestCheckoutCompletion(RecordGuestCheckoutCompletion command) {
        var events = commandHandler.handle(command.groupCheckoutId(), groupCheckout -> {
            if (groupCheckout.status() == CheckoutStatus.NOT_EXISTING) return List.of();

            return decide(command, groupCheckout);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command) {
        var events = commandHandler.handle(command.groupCheckoutId(), groupCheckout -> {
            if (groupCheckout.status() == CheckoutStatus.NOT_EXISTING) return List.of();

            return decide(command, groupCheckout);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }
}
