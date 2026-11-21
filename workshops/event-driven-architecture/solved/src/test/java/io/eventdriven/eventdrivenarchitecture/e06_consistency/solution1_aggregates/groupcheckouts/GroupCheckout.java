package io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.groupcheckouts;

import static io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.groupcheckouts.GroupCheckoutEvent.*;

import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.AbstractAggregate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class GroupCheckout extends AbstractAggregate<GroupCheckoutEvent, UUID> {
    private Map<UUID, CheckoutStatus> guestStayCheckouts = new LinkedHashMap<>();
    private CheckoutStatus status;

    public enum CheckoutStatus {
        INITIATED,
        COMPLETED,
        FAILED
    }

    // Default constructor for Jackson
    private GroupCheckout() {}

    public static GroupCheckout initiate(
            UUID groupCheckoutId, UUID clerkId, UUID[] guestStayAccountIds, OffsetDateTime initiatedAt) {
        var checkOut = new GroupCheckout();

        checkOut.enqueue(new GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStayAccountIds, initiatedAt));

        return checkOut;
    }

    public void recordGuestCheckoutCompletion(UUID guestStayAccountId, OffsetDateTime now) {
        if (status != CheckoutStatus.INITIATED
                || this.guestStayCheckouts.get(guestStayAccountId) == CheckoutStatus.COMPLETED) return;

        var guestCheckoutCompleted = new GuestCheckoutCompletionRecorded(id(), guestStayAccountId, now);

        enqueue(guestCheckoutCompleted);

        if (!areAnyOngoingCheckouts()) enqueue(finish(now));
    }

    public void recordGuestCheckoutFailure(UUID guestStayAccountId, OffsetDateTime now) {
        if (status != CheckoutStatus.INITIATED
                || this.guestStayCheckouts.get(guestStayAccountId) == CheckoutStatus.FAILED) return;

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
