package io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates;

import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core.Database;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.groupcheckouts.GroupCheckoutEvent;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.gueststayaccounts.GuestStayAccount;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class GuestStayFacade {
    private final Database.Collection<GuestStayAccount> collection;
    private final MessageBus messageBus;

    public GuestStayFacade(Database.Collection<GuestStayAccount> collection, MessageBus messageBus) {
        this.collection = collection;
        this.messageBus = messageBus;
    }

    public void checkInGuest(GuestStayAccountCommand.CheckInGuest command) {
        var account = GuestStayAccount.checkIn(command.guestStayAccountId(), command.now());

        collection.store(command.guestStayAccountId(), account);
        messageBus.publish(account.dequeueUncommittedEvents());
    }

    public void recordCharge(GuestStayAccountCommand.RecordCharge command) {
        var account = collection
                .get(command.guestStayAccountId())
                .orElseThrow(() -> new IllegalStateException("Entity not found"));

        account.recordCharge(command.amount(), command.now());

        collection.store(command.guestStayAccountId(), account);
        messageBus.publish(account.dequeueUncommittedEvents());
    }

    public void recordPayment(GuestStayAccountCommand.RecordPayment command) {
        var account = collection
                .get(command.guestStayAccountId())
                .orElseThrow(() -> new IllegalStateException("Entity not found"));

        account.recordPayment(command.amount(), command.now());

        collection.store(command.guestStayAccountId(), account);
        messageBus.publish(account.dequeueUncommittedEvents());
    }

    public void checkOutGuest(GuestStayAccountCommand.CheckOutGuest command) {
        var account = collection
                .get(command.guestStayAccountId())
                .orElseThrow(() -> new IllegalStateException("Entity not found"));

        account.checkout(command.now(), command.groupCheckoutId());

        collection.store(command.guestStayAccountId(), account);
        messageBus.publish(account.dequeueUncommittedEvents());
    }

    public void initiateGroupCheckout(GroupCheckoutCommand.InitiateGroupCheckout command) {
        messageBus.publish(List.of(new GroupCheckoutEvent.GroupCheckoutInitiated(
                command.groupCheckoutId(), command.clerkId(), command.guestStayAccountIds(), command.now())));
    }

    public sealed interface GuestStayAccountCommand {
        record CheckInGuest(UUID guestStayAccountId, OffsetDateTime now) implements GuestStayAccountCommand {}

        record RecordCharge(UUID guestStayAccountId, double amount, OffsetDateTime now)
                implements GuestStayAccountCommand {}

        record RecordPayment(UUID guestStayAccountId, double amount, OffsetDateTime now)
                implements GuestStayAccountCommand {}

        record CheckOutGuest(UUID guestStayAccountId, OffsetDateTime now, UUID groupCheckoutId)
                implements GuestStayAccountCommand {
            public CheckOutGuest(UUID guestStayAccountId, OffsetDateTime now) {
                this(guestStayAccountId, now, null);
            }
        }
    }

    public sealed interface GroupCheckoutCommand {
        record InitiateGroupCheckout(UUID groupCheckoutId, UUID clerkId, UUID[] guestStayAccountIds, OffsetDateTime now)
                implements GroupCheckoutCommand {}
    }
}
