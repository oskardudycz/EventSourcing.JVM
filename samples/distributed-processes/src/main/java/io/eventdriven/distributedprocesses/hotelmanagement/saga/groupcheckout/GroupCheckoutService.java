package io.eventdriven.distributedprocesses.hotelmanagement.saga.groupcheckout;

import io.eventdriven.distributedprocesses.core.commands.CommandHandler;

public class GroupCheckoutService {
  private final CommandHandler<GroupCheckout, GroupCheckoutCommand, GroupCheckoutEvent> commandHandler;

  public GroupCheckoutService(CommandHandler<GroupCheckout, GroupCheckoutCommand, GroupCheckoutEvent> commandHandler) {
    this.commandHandler = commandHandler;
  }

//  public ETag handle(InitiateGroupCheckout command) {
//    return store.add(
//      GroupCheckout.initiate(
//        command.groupCheckoutId(),
//        command.clerkId(),
//        command.guestStayAccountIds(),
//        OffsetDateTime.now()
//      )
//    );
//  }
//
//  public ETag handle(RecordGuestStayInitiation command) {
//    return store.getAndUpdate(
//      current -> current.recordGuestStaysCheckoutInitiation(
//        command.guestStayAccountIds(),
//        OffsetDateTime.now()
//      ),
//      command.groupCheckoutId()
//    );
//  }
//
//  public ETag handle(RecordGuestCheckoutCompletion command) {
//    return store.getAndUpdate(
//      current -> current.recordGuestStayCheckoutCompletion(
//        command.guestStayAccountId(),
//        OffsetDateTime.now()
//      ),
//      command.groupCheckoutId()
//    );
//  }
//
//  public ETag handle(RecordGuestCheckoutFailure command) {
//    return store.getAndUpdate(
//      current -> current.recordGuestStayCheckoutFailure(
//        command.guestStayAccountId(),
//        OffsetDateTime.now()
//      ),
//      command.groupCheckoutId()
//    );
//  }
}
