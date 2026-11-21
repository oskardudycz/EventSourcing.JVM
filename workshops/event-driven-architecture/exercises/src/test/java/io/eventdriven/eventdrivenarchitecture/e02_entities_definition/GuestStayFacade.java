package io.eventdriven.eventdrivenarchitecture.e02_entities_definition;

import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core.Database;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.gueststayaccounts.GuestStayAccount;
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
        // TODO: Fill the implementation calling your entity/aggregate
        throw new RuntimeException("TODO: Fill the implementation calling your entity/aggregate");
    }

    public void recordCharge(GuestStayAccountCommand.RecordCharge command) {
        var account = collection
                .get(command.guestStayAccountId())
                .orElseThrow(() -> new IllegalStateException("Entity not found"));

        // TODO: Fill the implementation calling your entity/aggregate
        // account.doSomething;
        var events = List.of();

        collection.store(command.guestStayAccountId(), account);
        messageBus.publish(events);

        throw new RuntimeException("TODO: Fill the implementation calling your entity/aggregate");
    }

    public void recordPayment(GuestStayAccountCommand.RecordPayment command) {
        var account = collection
                .get(command.guestStayAccountId())
                .orElseThrow(() -> new IllegalStateException("Entity not found"));

        // TODO: Fill the implementation calling your entity/aggregate
        // account.doSomething;
        var events = List.of();

        collection.store(command.guestStayAccountId(), account);
        messageBus.publish(events);

        throw new RuntimeException("TODO: Fill the implementation calling your entity/aggregate");
    }

    public void checkOutGuest(GuestStayAccountCommand.CheckOutGuest command) {
        var account = collection
                .get(command.guestStayAccountId())
                .orElseThrow(() -> new IllegalStateException("Entity not found"));

        // TODO: Fill the implementation calling your entity/aggregate
        // account.doSomething;
        var events = List.of();

        collection.store(command.guestStayAccountId(), account);
        messageBus.publish(events);

        throw new RuntimeException("TODO: Fill the implementation calling your entity/aggregate");
    }

    public void initiateGroupCheckout(GroupCheckoutCommand.InitiateGroupCheckout command) {
        // TODO: Fill the implementation publishing event
        throw new RuntimeException("TODO: Fill the implementation publishing event");
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
