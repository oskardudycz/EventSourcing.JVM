package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution1_aggregates.groupcheckouts;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.core.AggregateStore;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public class GroupCheckoutFacade {
    private final AggregateStore<GroupCheckout> store;
    private final MessageBus messageBus;

    public GroupCheckoutFacade(Database.Collection<GroupCheckout> collection, MessageBus messageBus) {
        this.store = new AggregateStore<>(collection);
        this.messageBus = messageBus;
    }

    public void initiateGroupCheckout(InitiateGroupCheckout command) {
        var events = store.handle(command.groupCheckoutId(), groupCheckout -> {
            if (groupCheckout.isPresent()) return Optional.empty();

            return Optional.of(GroupCheckout.initiate(
                    command.groupCheckoutId(), command.clerkId(), command.guestStayIds(), command.now()));
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void recordGuestCheckoutCompletion(RecordGuestCheckoutCompletion command) {
        var events = store.handle(
                command.groupCheckoutId(),
                groupCheckout -> groupCheckout.map(existing -> {
                    existing.recordGuestCheckoutCompletion(command.guestStayId(), command.completedAt());

                    return existing;
                }));

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command) {
        var events = store.handle(
                command.groupCheckoutId(),
                groupCheckout -> groupCheckout.map(existing -> {
                    existing.recordGuestCheckoutFailure(command.guestStayId(), command.failedAt());

                    return existing;
                }));

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public sealed interface GroupCheckoutCommand {
        record InitiateGroupCheckout(UUID groupCheckoutId, UUID clerkId, UUID[] guestStayIds, OffsetDateTime now)
                implements GroupCheckoutCommand {}

        record RecordGuestCheckoutCompletion(UUID groupCheckoutId, UUID guestStayId, OffsetDateTime completedAt)
                implements GroupCheckoutCommand {}

        record RecordGuestCheckoutFailure(UUID groupCheckoutId, UUID guestStayId, OffsetDateTime failedAt)
                implements GroupCheckoutCommand {}
    }
}
