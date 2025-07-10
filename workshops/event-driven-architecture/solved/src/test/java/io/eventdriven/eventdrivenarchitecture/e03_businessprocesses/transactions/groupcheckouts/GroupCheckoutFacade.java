package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.CommandBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.EventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts.GuestStayAccount;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts.GuestStayAccountDecider;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts.GuestStayAccountEvent;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;

public class GroupCheckoutFacade {
  private final Database database;
  private final EventBus eventBus;
  private final CommandBus commandBus;

  public GroupCheckoutFacade(Database database, EventBus eventBus, CommandBus commandBus) {
    this.database = database;
    this.eventBus = eventBus;
    this.commandBus = commandBus;
  }

  public void initiateGroupCheckout(GroupCheckoutCommand.InitiateGroupCheckout command) {
    database.transaction(db -> {
      var events = new ArrayList<>();
      for(var guestStayAccountId: command.guestStayIds()){
        var account = db.get(GuestStayAccount.class, guestStayAccountId).orElseThrow();
        var event = GuestStayAccountDecider.decide(new CheckOutGuest(guestStayAccountId, command.now(), command.groupCheckoutId()), account);

        db.store(command.groupCheckoutId(), GuestStayAccount.evolve(account, event));
        events.add(event);
      }

      var completed = events.stream()
        .filter(GuestStayAccountEvent.GuestCheckedOut.class::isInstance)
        .map(GuestStayAccountEvent.GuestCheckedOut.class::cast)
        .toList();

      var failed = events.stream()
        .filter(GuestStayAccountEvent.GuestCheckoutFailed.class::isInstance)
        .map(GuestStayAccountEvent.GuestCheckoutFailed.class::cast)
        .toList();

      if(!failed.isEmpty()){
        events.add(new GroupCheckoutEvent.GroupCheckoutFailed(
          command.groupCheckoutId(),
          completed.stream().map(e -> e.guestStayAccountId()).toArray(UUID[]::new),
          failed.stream().map(e -> e.guestStayAccountId()).toArray(UUID[]::new),
          command.now()
        ));
      } else {
        events.add(new GroupCheckoutEvent.GroupCheckoutCompleted(
          command.groupCheckoutId(),
          completed.stream().map(e -> e.guestStayAccountId()).toArray(UUID[]::new),
          command.now()
        ));
      }

      eventBus.publish(events.toArray());
    });
  }

  public sealed interface GroupCheckoutCommand {
    record InitiateGroupCheckout(
      UUID groupCheckoutId,
      UUID clerkId,
      UUID[] guestStayIds,
      OffsetDateTime now
    ) implements GroupCheckoutCommand {
    }
  }
}
