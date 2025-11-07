package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.groupcheckouts;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.groupcheckouts.GroupCheckout.CheckoutStatus;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.groupcheckouts.GroupCheckout.evolve;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.groupcheckouts.GroupCheckoutEvent.*;

public final class GroupCheckoutDecider {

  public static GroupCheckoutInitiated handle(
    InitiateGroupCheckout command
  ) {
    return new GroupCheckoutInitiated(
      command.groupCheckoutId(),
      command.clerkId(),
      command.guestStayIds(),
      command.now()
    );
  }

  public static GroupCheckoutEvent[] handle(
    RecordGuestCheckoutCompletion command,
    GroupCheckout state
  ) {
    var guestStayId = command.guestStayId();
    var now = command.now();

    if (state.status() != CheckoutStatus.INITIATED || state.recordedAlreadyGuestCheckoutWithStatus(guestStayId, CheckoutStatus.COMPLETED))
      return new GroupCheckoutEvent[]{};

    var guestCheckoutCompleted = new GuestCheckoutCompletionRecorded(
      state.id(),
      guestStayId,
      now
    );

    state = evolve(state, guestCheckoutCompleted);

    return state.areAnyOngoingCheckouts() ?
      new GroupCheckoutEvent[]{guestCheckoutCompleted}
      : new GroupCheckoutEvent[]{guestCheckoutCompleted, finalize(state, now)};
  }


  public static GroupCheckoutEvent[] handle(
    RecordGuestCheckoutFailure command,
    GroupCheckout state
  ) {
    var guestStayId = command.guestStayId();
    var now = command.now();

    if (state.status() != CheckoutStatus.INITIATED || state.recordedAlreadyGuestCheckoutWithStatus(guestStayId, CheckoutStatus.FAILED))
      return new GroupCheckoutEvent[]{};

    var guestCheckoutFailed = new GuestCheckoutFailureRecorded(
      state.id(),
      guestStayId,
      now
    );

    state = evolve(state, guestCheckoutFailed);

    return state.areAnyOngoingCheckouts() ?
      new GroupCheckoutEvent[]{guestCheckoutFailed}
      : new GroupCheckoutEvent[]{guestCheckoutFailed, finalize(state, now)};
  }

  private static GroupCheckoutEvent finalize(
    GroupCheckout state,
    OffsetDateTime now
  ) {
    return !state.areAnyFailedCheckouts() ?
      new GroupCheckoutCompleted(
        state.id(),
        state.checkoutsWith(CheckoutStatus.COMPLETED),
        now
      )
      :
      new GroupCheckoutFailed(
        state.id(),
        state.checkoutsWith(CheckoutStatus.COMPLETED),
        state.checkoutsWith(CheckoutStatus.FAILED),
        now
      );
  }


  public static GroupCheckoutEvent[] decide(GroupCheckoutCommand command, GroupCheckout state) {
    return switch (command) {
      case InitiateGroupCheckout initiateGroupCheckout ->
        new GroupCheckoutEvent[]{handle(initiateGroupCheckout)};
      case RecordGuestCheckoutCompletion recordGuestCheckoutCompletion ->
        handle(recordGuestCheckoutCompletion, state);
      case RecordGuestCheckoutFailure recordGuestCheckoutFailure ->
        handle(recordGuestCheckoutFailure, state);
    };
  }

  public sealed interface GroupCheckoutCommand {
    record InitiateGroupCheckout(
      UUID groupCheckoutId,
      UUID clerkId,
      UUID[] guestStayIds,
      OffsetDateTime now
    ) implements GroupCheckoutCommand {
    }

    record RecordGuestCheckoutCompletion(
      UUID groupCheckoutId,
      UUID guestStayId,
      OffsetDateTime now
    ) implements GroupCheckoutCommand {
    }

    record RecordGuestCheckoutFailure(
      UUID groupCheckoutId,
      UUID guestStayId,
      OffsetDateTime now
    ) implements GroupCheckoutCommand {
    }
  }
}
