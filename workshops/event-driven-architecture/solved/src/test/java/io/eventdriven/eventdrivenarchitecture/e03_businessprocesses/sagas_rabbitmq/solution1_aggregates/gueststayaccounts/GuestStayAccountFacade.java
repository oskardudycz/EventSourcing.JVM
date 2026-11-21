package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.micrometer.observation.annotation.Observed;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
@Observed(name = "guest.stay.account")
public class GuestStayAccountFacade {
    private final Database.Collection<GuestStayAccount> collection;
    private final MessageBus messageBus;

    public GuestStayAccountFacade(Database database, MessageBus messageBus) {
        this.collection = database.collection(GuestStayAccount.class);
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
        var account = collection.get(command.guestStayAccountId()).orElse(null);
        if (account == null) return;

        account.checkout(command.now(), command.groupCheckoutId());

        collection.store(command.guestStayAccountId(), account);
        messageBus.publish(account.dequeueUncommittedEvents());
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
}
