package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution2_immutableentities.groupcheckouts;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.FunctionalTools.FoldLeft.reduce;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution2_immutableentities.groupcheckouts.GroupCheckout.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.decide;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Retry;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Versioned;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution2_immutableentities.groupcheckouts.GroupCheckout.CheckoutStatus;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class GroupCheckoutFacade {
    private final Database.Collection<GroupCheckout> collection;
    private final MessageBus messageBus;

    public GroupCheckoutFacade(Database.Collection<GroupCheckout> collection, MessageBus messageBus) {
        this.collection = collection;
        this.messageBus = messageBus;
    }

    public void initiateGroupCheckout(InitiateGroupCheckout command) {
        var events = handle(command.groupCheckoutId(), groupCheckout -> {
            if (groupCheckout.status() != CheckoutStatus.NOT_EXISTING) return List.of();

            return decide(command, groupCheckout);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void recordGuestCheckoutCompletion(RecordGuestCheckoutCompletion command) {
        var events = handle(command.groupCheckoutId(), groupCheckout -> {
            if (groupCheckout.status() == CheckoutStatus.NOT_EXISTING) return List.of();

            return decide(command, groupCheckout);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command) {
        var events = handle(command.groupCheckoutId(), groupCheckout -> {
            if (groupCheckout.status() == CheckoutStatus.NOT_EXISTING) return List.of();

            return decide(command, groupCheckout);
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    private List<GroupCheckoutEvent> handle(
            UUID groupCheckoutId, Function<GroupCheckout, List<GroupCheckoutEvent>> handle) {
        return Retry.onConflict(() -> {
            var loaded = collection.get(groupCheckoutId);
            var groupCheckout = loaded.map(Versioned::document).orElse(INITIAL);

            var events = handle.apply(groupCheckout);
            if (events.isEmpty()) return events;

            collection.store(
                    groupCheckoutId,
                    reduce(events, groupCheckout, GroupCheckout::evolve),
                    loaded.map(Versioned::version).orElse(0));

            return events;
        });
    }
}
