package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution1_aggregates.groupcheckouts;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Retry;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Versioned;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
            if (groupCheckout.isPresent()) return Optional.empty();

            return Optional.of(GroupCheckout.initiate(
                    command.groupCheckoutId(), command.clerkId(), command.guestStayAccountIds(), command.now()));
        });

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void recordGuestCheckoutCompletion(RecordGuestCheckoutCompletion command) {
        var events = handle(
                command.groupCheckoutId(),
                groupCheckout -> groupCheckout.map(existing -> {
                    existing.recordGuestCheckoutCompletion(command.guestStayAccountId(), command.now());

                    return existing;
                }));

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command) {
        var events = handle(
                command.groupCheckoutId(),
                groupCheckout -> groupCheckout.map(existing -> {
                    existing.recordGuestCheckoutFailure(command.guestStayAccountId(), command.now());

                    return existing;
                }));

        if (events.isEmpty()) return;

        messageBus.publish(events);
    }

    private List<Object> handle(
            UUID groupCheckoutId, Function<Optional<GroupCheckout>, Optional<GroupCheckout>> handle) {
        return Retry.onConflict(() -> {
            var loaded = collection.get(groupCheckoutId);

            var groupCheckout = handle.apply(loaded.map(Versioned::document));
            if (groupCheckout.isEmpty()) return List.of();

            var events = groupCheckout.get().dequeueUncommittedEvents();
            if (events.isEmpty()) return events;

            collection.store(
                    groupCheckoutId,
                    groupCheckout.get(),
                    loaded.map(Versioned::version).orElse(0));

            return events;
        });
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
