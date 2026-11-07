package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckout.CheckoutStatus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountEvent.GuestCheckedOut;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountEvent.GuestCheckoutFailed;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckoutEvent.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckOutGuest;

public class GroupCheckoutToDoList {
  private final Database.Collection<GroupCheckout> collection;
  private final MessageBus messageBus;

  public GroupCheckoutToDoList(Database.Collection<GroupCheckout> collection, MessageBus messageBus) {
    this.collection = collection;
    this.messageBus = messageBus;
  }

  public void initiateGroupCheckout(GroupCheckoutCommand.InitiateGroupCheckout command) {
    collection.store(command.groupCheckoutId(), GroupCheckout.initiated(command.groupCheckoutId(), command.guestStayIds()));

    messageBus.publish(List.of(
      new GroupCheckoutInitiated(command.groupCheckoutId(), command.clerkId(), command.guestStayIds(), command.now())
    ));
  }

  public void groupCheckoutInitiated(GroupCheckoutInitiated event) {
    messageBus.publish(
      Arrays.stream(event.guestStayAccountIds())
        .map(guestStayId -> new CheckOutGuest(guestStayId, event.initiatedAt(), event.groupCheckoutId()))
        .toList()
    );
  }

  public void guestCheckedOut(GuestCheckedOut event) {
    if (event.groupCheckoutId() == null)
      return;

    recordCheckout(event.groupCheckoutId(), event.guestStayAccountId(), CheckoutStatus.COMPLETED, event.completedAt());
  }

  public void guestCheckoutFailed(GuestCheckoutFailed event) {
    if (event.groupCheckoutId() == null)
      return;

    recordCheckout(event.groupCheckoutId(), event.guestStayAccountId(), CheckoutStatus.FAILED, event.failedAt());
  }

  private void recordCheckout(UUID groupCheckoutId, UUID guestStayId, CheckoutStatus status, OffsetDateTime now) {
    var groupCheckout = collection.get(groupCheckoutId)
      .orElseThrow(() -> new IllegalStateException("Entity not found"))
      .withCheckout(guestStayId, status);

    collection.store(groupCheckoutId, groupCheckout);

    tryComplete(groupCheckout, now)
      .ifPresent(completed -> messageBus.publish(List.of(completed)));
  }

  // This could be also checked by Cron
  private static Optional<GroupCheckoutEvent> tryComplete(GroupCheckout groupCheckout, OffsetDateTime now) {
    if (groupCheckout.areAnyOngoingCheckouts())
      return Optional.empty();

    return Optional.of(
      !groupCheckout.areAnyFailedCheckouts()
        ? new GroupCheckoutCompleted(
        groupCheckout.id(),
        groupCheckout.checkoutsWith(CheckoutStatus.COMPLETED),
        now
      )
        : new GroupCheckoutFailed(
        groupCheckout.id(),
        groupCheckout.checkoutsWith(CheckoutStatus.COMPLETED),
        groupCheckout.checkoutsWith(CheckoutStatus.FAILED),
        now
      )
    );
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
