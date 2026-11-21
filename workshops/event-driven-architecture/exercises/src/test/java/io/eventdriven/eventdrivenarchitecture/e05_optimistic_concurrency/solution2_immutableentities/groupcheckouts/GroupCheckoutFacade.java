package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.FunctionalTools.FoldLeft.reduce;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts.GroupCheckout.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.decide;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Versioned;

public class GroupCheckoutFacade {
    private final Database.Collection<GroupCheckout> collection;
    private final MessageBus messageBus;

    public GroupCheckoutFacade(Database.Collection<GroupCheckout> collection, MessageBus messageBus) {
        this.collection = collection;
        this.messageBus = messageBus;
    }

    public void initiateGroupCheckout(InitiateGroupCheckout command) {
        if (collection.get(command.groupCheckoutId()).isPresent()) return;

        var events = decide(command, INITIAL);

        collection.store(command.groupCheckoutId(), reduce(events, INITIAL, GroupCheckout::evolve));
        messageBus.publish(events);
    }

    public void recordGuestCheckoutCompletion(RecordGuestCheckoutCompletion command) {
        final var groupCheckout = collection
                .get(command.groupCheckoutId())
                .map(Versioned::document)
                .orElse(null);
        if (groupCheckout == null) return;

        var events = decide(command, groupCheckout);

        collection.store(command.groupCheckoutId(), reduce(events, groupCheckout, GroupCheckout::evolve));
        messageBus.publish(events);
    }

    public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command) {
        var groupCheckout = collection
                .get(command.groupCheckoutId())
                .map(Versioned::document)
                .orElse(null);
        if (groupCheckout == null) return;

        var events = decide(command, groupCheckout);

        collection.store(command.groupCheckoutId(), reduce(events, groupCheckout, GroupCheckout::evolve));
        messageBus.publish(events);
    }
}
