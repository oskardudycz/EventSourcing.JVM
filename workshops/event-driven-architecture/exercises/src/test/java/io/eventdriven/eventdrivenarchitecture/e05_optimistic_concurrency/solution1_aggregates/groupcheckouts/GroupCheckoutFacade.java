package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.groupcheckouts;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Versioned;
import java.time.OffsetDateTime;
import java.util.UUID;

public class GroupCheckoutFacade {
    private final Database.Collection<GroupCheckout> collection;
    private final MessageBus messageBus;

    public GroupCheckoutFacade(Database.Collection<GroupCheckout> collection, MessageBus messageBus) {
        this.collection = collection;
        this.messageBus = messageBus;
    }

    public void initiateGroupCheckout(InitiateGroupCheckout command) {
        if (collection.get(command.groupCheckoutId()).isPresent()) return;

        var groupCheckout = GroupCheckout.initiate(
                command.groupCheckoutId(), command.clerkId(), command.guestStayAccountIds(), command.now());

        collection.store(command.groupCheckoutId(), groupCheckout);
        messageBus.publish(groupCheckout.dequeueUncommittedEvents());
    }

    public void recordGuestCheckoutCompletion(RecordGuestCheckoutCompletion command) {
        var groupCheckout = collection
                .get(command.groupCheckoutId())
                .map(Versioned::document)
                .orElse(null);
        if (groupCheckout == null) return;

        groupCheckout.recordGuestCheckoutCompletion(command.guestStayAccountId(), command.now());

        collection.store(command.groupCheckoutId(), groupCheckout);
        messageBus.publish(groupCheckout.dequeueUncommittedEvents());
    }

    public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command) {
        var groupCheckout = collection
                .get(command.groupCheckoutId())
                .map(Versioned::document)
                .orElse(null);
        if (groupCheckout == null) return;

        groupCheckout.recordGuestCheckoutFailure(command.guestStayAccountId(), command.now());

        collection.store(command.groupCheckoutId(), groupCheckout);
        messageBus.publish(groupCheckout.dequeueUncommittedEvents());
    }

    public sealed interface GroupCheckoutCommand {
        record InitiateGroupCheckout(UUID groupCheckoutId, UUID clerkId, UUID[] guestStayAccountIds, OffsetDateTime now)
                implements GroupCheckoutCommand {}

        record RecordGuestCheckoutCompletion(UUID groupCheckoutId, UUID guestStayAccountId, OffsetDateTime now)
                implements GroupCheckoutCommand {}

        record RecordGuestCheckoutFailure(UUID groupCheckoutId, UUID guestStayAccountId, OffsetDateTime now)
                implements GroupCheckoutCommand {}
    }
}
