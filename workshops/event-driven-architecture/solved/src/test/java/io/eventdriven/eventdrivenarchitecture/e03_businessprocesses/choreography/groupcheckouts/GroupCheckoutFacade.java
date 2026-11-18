package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.groupcheckouts;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.groupcheckouts.GroupCheckout.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.groupcheckouts.GroupCheckoutDecider.decide;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.FunctionalTools.FoldLeft.reduce;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.gueststayaccounts.GuestStayAccountDecider;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.gueststayaccounts.GuestStayAccountEvent;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import java.util.Arrays;

public class GroupCheckoutFacade {
    private final Database.Collection<GroupCheckout> collection;
    private final MessageBus messageBus;

    public GroupCheckoutFacade(Database.Collection<GroupCheckout> collection, MessageBus messageBus) {
        this.collection = collection;
        this.messageBus = messageBus;
    }

    public void initiateGroupCheckout(InitiateGroupCheckout command) {
        var events = decide(command, INITIAL);

        collection.store(command.groupCheckoutId(), reduce(events, INITIAL, GroupCheckout::evolve));
        messageBus.publish(events);

        messageBus.publish(events.stream()
                .filter(GroupCheckoutEvent.GroupCheckoutInitiated.class::isInstance)
                .map(GroupCheckoutEvent.GroupCheckoutInitiated.class::cast)
                .flatMap(e -> Arrays.stream(e.guestStayAccountIds())
                        .map(id -> new GuestStayAccountDecider.GuestStayAccountCommand.CheckOutGuest(
                                id, e.initiatedAt(), e.groupCheckoutId())))
                .toList());
    }

    public void recordGuestCheckoutCompletion(GuestStayAccountEvent.GuestCheckedOut event) {
        final var groupCheckout = collection.get(event.groupCheckoutId()).orElse(null);
        if (groupCheckout == null) return;

        var events = decide(
                new RecordGuestCheckoutCompletion(
                        event.groupCheckoutId(), event.guestStayAccountId(), event.completedAt()),
                groupCheckout);

        collection.store(event.groupCheckoutId(), reduce(events, groupCheckout, GroupCheckout::evolve));
        messageBus.publish(events);
    }

    public void recordGuestCheckoutFailure(GuestStayAccountEvent.GuestCheckoutFailed event) {
        var groupCheckout = collection.get(event.groupCheckoutId()).orElse(null);
        if (groupCheckout == null) return;

        var events = decide(
                new RecordGuestCheckoutFailure(event.groupCheckoutId(), event.guestStayAccountId(), event.failedAt()),
                groupCheckout);

        collection.store(event.groupCheckoutId(), reduce(events, groupCheckout, GroupCheckout::evolve));
        messageBus.publish(events);
    }
}
