package io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.entities.CommandHandler;
import io.eventdriven.distributedprocesses.core.events.EventBus;
import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.distributedprocesses.core.retries.RetryPolicy;
import io.eventdriven.distributedprocesses.hotelmanagement.saga.groupcheckout.GroupCheckout;
import io.eventdriven.distributedprocesses.hotelmanagement.saga.groupcheckout.GroupCheckoutCommand;
import io.eventdriven.distributedprocesses.hotelmanagement.saga.groupcheckout.GroupCheckoutEvent;
import io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountCommand.CheckOutGuest;
import io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountCommand.CheckInGuest;
import io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountCommand.RecordCharge;
import io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountCommand.RecordPayment;

import java.time.OffsetDateTime;
import java.util.UUID;

public class GuestStayAccountService {
  private final CommandHandler<GroupCheckout, GroupCheckoutCommand, GroupCheckoutEvent> commandHandler;

  public GuestStayAccountService(
    CommandHandler<GroupCheckout, GroupCheckoutCommand, GroupCheckoutEvent> commandHandler
  ) {
    this.commandHandler = commandHandler;
  }
//
//  public ETag handle(CheckInGuest command) {
//    return store.add(
//      GuestStayAccount.open(
//        command.guestStayAccountId(),
//        OffsetDateTime.now()
//      )
//    );
//  }
//
//  public ETag handle(RecordCharge command) {
//    return store.getAndUpdate(
//      current -> current.recordCharge(
//        command.amount(),
//        OffsetDateTime.now()
//      ),
//      command.guestStayAccountId()
//    );
//  }
//
//  public ETag handle(RecordPayment command) {
//    return store.getAndUpdate(
//      current -> current.recordPayment(
//        command.amount(),
//        OffsetDateTime.now()
//      ),
//      command.guestStayAccountId()
//    );
//  }
//
//  public ETag handle(CheckOutGuest command) {
//    return retryPolicy.run(ack -> {
//      var result = store.getAndUpdate(
//        current -> current.checkout(
//          command.guestStayAccountId(),
//          OffsetDateTime.now()
//        ),
//        command.guestStayAccountId()
//      );
//      ack.accept(result);
//    });
//  }
}
