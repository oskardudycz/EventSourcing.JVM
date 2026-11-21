package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayAccount.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayAccount.evolve;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.decide;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;
import java.util.List;

public class GuestStayAccountFacade {
    private final Database.Collection<GuestStayAccount> collection;
    private final MessageBus messageBus;

    public GuestStayAccountFacade(Database.Collection<GuestStayAccount> collection, MessageBus messageBus) {
        this.collection = collection;
        this.messageBus = messageBus;
    }

    public void checkInGuest(CheckInGuest command) {
        var checkedIn = decide(command, INITIAL);

        collection.store(command.guestStayAccountId(), evolve(INITIAL, checkedIn));
        messageBus.publish(List.of(checkedIn));
    }

    public void recordCharge(RecordCharge command) {
        var account = collection
                .get(command.guestStayAccountId())
                .orElseThrow(() -> new IllegalStateException("Entity not found"));

        var chargeRecorded = decide(command, account);

        collection.store(command.guestStayAccountId(), evolve(account, chargeRecorded));
        messageBus.publish(List.of(chargeRecorded));
    }

    public void recordPayment(RecordPayment command) {
        var account = collection
                .get(command.guestStayAccountId())
                .orElseThrow(() -> new IllegalStateException("Entity not found"));

        var recordPayment = decide(command, account);

        collection.store(command.guestStayAccountId(), evolve(account, recordPayment));
        messageBus.publish(List.of(recordPayment));
    }

    public void checkOutGuest(CheckOutGuest command) {
        var account = collection.get(command.guestStayAccountId()).orElse(null);
        if (account == null) return;

        var checkedOut = decide(command, account);

        collection.store(command.guestStayAccountId(), evolve(account, checkedOut));
        messageBus.publish(List.of(checkedOut));
    }
}
