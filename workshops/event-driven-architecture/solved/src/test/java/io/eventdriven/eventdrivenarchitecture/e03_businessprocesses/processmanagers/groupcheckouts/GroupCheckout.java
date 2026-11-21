package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.groupcheckouts;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.groupcheckouts.GroupCheckoutEvent.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.gueststayaccounts.GuestStayAccountFacade.GuestStayAccountCommand;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.core.AbstractProcessManager;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.gueststayaccounts.GuestStayAccountEvent;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class GroupCheckout extends AbstractProcessManager<GroupCheckoutEvent, UUID> {
    private Map<UUID, CheckoutStatus> guestStayCheckouts = new LinkedHashMap<>();
    private CheckoutStatus status;

    public enum CheckoutStatus {
        INITIATED,
        COMPLETED,
        FAILED
    }

    // Default constructor for Jackson
    private GroupCheckout() {}

    public static GroupCheckout handle(GroupCheckoutCommand.InitiateGroupCheckout command) {
        var groupCheckoutId = command.groupCheckoutId();
        var clerkId = command.clerkId();
        var guestStayAccountIds = command.guestStayAccountIds();
        var initiatedAt = command.now();

        var state = new GroupCheckout();

        state.enqueue(new GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStayAccountIds, initiatedAt));

        Arrays.stream(guestStayAccountIds)
                .map(guestAccountId ->
                        new GuestStayAccountCommand.CheckOutGuest(guestAccountId, initiatedAt, groupCheckoutId))
                .forEach(state::schedule);

        return state;
    }

    public void on(GuestStayAccountEvent.GuestCheckedOut event) {
        var guestStayAccountId = event.guestStayAccountId();
        var now = event.completedAt();

        if (status != CheckoutStatus.INITIATED) return;

        var guestCheckoutCompleted = new GuestCheckoutCompletionRecorded(id(), guestStayAccountId, now);

        enqueue(guestCheckoutCompleted);

        if (!areAnyOngoingCheckouts()) enqueue(finish(now));
    }

    public void on(GuestStayAccountEvent.GuestCheckoutFailed event) {
        var guestStayAccountId = event.guestStayAccountId();
        var now = event.failedAt();

        if (status != CheckoutStatus.INITIATED) return;

        var guestCheckoutFailed = new GuestCheckoutFailureRecorded(id(), guestStayAccountId, now);

        enqueue(guestCheckoutFailed);

        if (!areAnyOngoingCheckouts()) enqueue(finish(now));
    }

    private GroupCheckoutEvent finish(OffsetDateTime now) {
        return !areAnyFailedCheckouts()
                ? new GroupCheckoutCompleted(id(), checkoutsWith(CheckoutStatus.COMPLETED), now)
                : new GroupCheckoutFailed(
                        id(), checkoutsWith(CheckoutStatus.COMPLETED), checkoutsWith(CheckoutStatus.FAILED), now);
    }

    @Override
    public void apply(GroupCheckoutEvent groupCheckoutEvent) {
        switch (groupCheckoutEvent) {
            case GroupCheckoutInitiated groupCheckoutInitiated -> {
                this.id = groupCheckoutInitiated.groupCheckoutId();
                this.guestStayCheckouts = Arrays.stream(groupCheckoutInitiated.guestStayAccountIds())
                        .collect(Collectors.toMap(
                                key -> key,
                                _ -> CheckoutStatus.INITIATED,
                                (existing, _) -> existing,
                                LinkedHashMap::new));
                this.status = CheckoutStatus.INITIATED;
            }
            case GuestCheckoutCompletionRecorded guestCheckoutCompleted -> {
                guestStayCheckouts.put(guestCheckoutCompleted.guestStayAccountId(), CheckoutStatus.COMPLETED);
            }
            case GuestCheckoutFailureRecorded guestCheckoutFailed -> {
                guestStayCheckouts.put(guestCheckoutFailed.guestStayAccountId(), CheckoutStatus.FAILED);
            }
            case GroupCheckoutCompleted groupCheckoutCompleted -> {
                this.status = CheckoutStatus.COMPLETED;
            }
            case GroupCheckoutFailed groupCheckoutFailed -> {
                this.status = CheckoutStatus.FAILED;
            }
        }
    }

    private boolean areAnyOngoingCheckouts() {
        return guestStayCheckouts.values().stream()
                .anyMatch(guestStayStatus -> guestStayStatus.equals(CheckoutStatus.INITIATED));
    }

    private boolean areAnyFailedCheckouts() {
        return guestStayCheckouts.values().stream()
                .anyMatch(guestStayStatus -> guestStayStatus.equals(CheckoutStatus.FAILED));
    }

    private UUID[] checkoutsWith(CheckoutStatus guestStayStatus) {
        return guestStayCheckouts.entrySet().stream()
                .filter(kv -> kv.getValue().equals(guestStayStatus))
                .map(Map.Entry::getKey)
                .toArray(UUID[]::new);
    }
}
