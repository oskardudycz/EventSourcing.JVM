package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.IEventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;
import org.springframework.stereotype.Service;

import java.util.List;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.gueststayaccounts.GuestStayAccount.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.gueststayaccounts.GuestStayAccount.evolve;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.decide;

@Service
public class GuestStayAccountFacade {
  private final Database.Collection<GuestStayAccount> collection;
  private final IEventBus eventBus;

  public GuestStayAccountFacade(Database database, IEventBus eventBus) {
    this.collection = database.collection(GuestStayAccount.class);
    this.eventBus = eventBus;
  }

  public void checkInGuest(CheckInGuest command) {
    var checkedIn = decide(command, INITIAL);

    collection.store(command.guestStayId(), evolve(INITIAL, checkedIn));
    eventBus.publish(List.of(checkedIn));
  }

  public void recordCharge(RecordCharge command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var chargeRecorded = decide(command, account);

    collection.store(command.guestStayId(), evolve(account, chargeRecorded));
    eventBus.publish(List.of(chargeRecorded));
  }

  public void recordPayment(RecordPayment command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var recordPayment = decide(command, account);

    collection.store(command.guestStayId(), evolve(account, recordPayment));
    eventBus.publish(List.of(recordPayment));
  }

  public void checkOutGuest(CheckOutGuest command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var checkedOut = decide(command, account);

    collection.store(command.guestStayId(), evolve(account, checkedOut));
    eventBus.publish(List.of(checkedOut));
  }
}
