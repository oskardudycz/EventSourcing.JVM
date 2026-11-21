package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.groupcheckouts;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.groupcheckouts.GroupCheckout.CheckoutStatus;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.groupcheckouts.GroupCheckout.evolve;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.groupcheckouts.GroupCheckoutEvent.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class GroupCheckoutDecider {

    public static GroupCheckoutInitiated handle(InitiateGroupCheckout command) {
        return new GroupCheckoutInitiated(
                command.groupCheckoutId(), command.clerkId(), command.guestStayAccountIds(), command.now());
    }

    public static List<GroupCheckoutEvent> handle(RecordGuestCheckoutCompletion command, GroupCheckout state) {
        var guestStayAccountId = command.guestStayAccountId();
        var now = command.now();

        if (state.status() != CheckoutStatus.INITIATED) return List.of();

        var guestCheckoutCompleted = new GuestCheckoutCompletionRecorded(state.id(), guestStayAccountId, now);

        state = evolve(state, guestCheckoutCompleted);

        return state.areAnyOngoingCheckouts()
                ? List.of(guestCheckoutCompleted)
                : List.of(guestCheckoutCompleted, finish(state, now));
    }

    public static List<GroupCheckoutEvent> handle(RecordGuestCheckoutFailure command, GroupCheckout state) {
        var guestStayAccountId = command.guestStayAccountId();
        var now = command.now();

        if (state.status() != CheckoutStatus.INITIATED) return List.of();

        var guestCheckoutFailed = new GuestCheckoutFailureRecorded(state.id(), guestStayAccountId, now);

        state = evolve(state, guestCheckoutFailed);

        return state.areAnyOngoingCheckouts()
                ? List.of(guestCheckoutFailed)
                : List.of(guestCheckoutFailed, finish(state, now));
    }

    private static GroupCheckoutEvent finish(GroupCheckout state, OffsetDateTime now) {
        return !state.areAnyFailedCheckouts()
                ? new GroupCheckoutCompleted(state.id(), state.checkoutsWith(CheckoutStatus.COMPLETED), now)
                : new GroupCheckoutFailed(
                        state.id(),
                        state.checkoutsWith(CheckoutStatus.COMPLETED),
                        state.checkoutsWith(CheckoutStatus.FAILED),
                        now);
    }

    public static List<GroupCheckoutEvent> decide(GroupCheckoutCommand command, GroupCheckout state) {
        return switch (command) {
            case InitiateGroupCheckout initiateGroupCheckout -> List.of(handle(initiateGroupCheckout));
            case RecordGuestCheckoutCompletion recordGuestCheckoutCompletion ->
                handle(recordGuestCheckoutCompletion, state);
            case RecordGuestCheckoutFailure recordGuestCheckoutFailure -> handle(recordGuestCheckoutFailure, state);
        };
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
