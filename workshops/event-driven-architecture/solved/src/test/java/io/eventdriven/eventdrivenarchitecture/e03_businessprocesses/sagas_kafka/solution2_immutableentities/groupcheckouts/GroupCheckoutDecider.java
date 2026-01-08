package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.groupcheckouts;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.groupcheckouts.GroupCheckout.CheckoutStatus;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.groupcheckouts.GroupCheckout.evolve;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.groupcheckouts.GroupCheckoutEvent.*;

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

  public static List<GroupCheckoutEvent> handle(
    RecordGuestCheckoutCompletion command,
    GroupCheckout state
  ) {
    var guestStayId = command.guestStayId();
    var now = command.now();

    if (state.status() != CheckoutStatus.INITIATED || state.recordedAlreadyGuestCheckoutWithStatus(guestStayId, CheckoutStatus.COMPLETED))
      return List.of();

    var guestCheckoutCompleted = new GuestCheckoutCompletionRecorded(
      state.id(),
      guestStayId,
      now
    );

    state = evolve(state, guestCheckoutCompleted);

    return state.areAnyOngoingCheckouts() ?
      List.of(guestCheckoutCompleted)
      : List.of(guestCheckoutCompleted, finalize(state, now));
  }


  public static List<GroupCheckoutEvent> handle(
    RecordGuestCheckoutFailure command,
    GroupCheckout state
  ) {
    var guestStayId = command.guestStayId();
    var now = command.now();

    if (state.status() != CheckoutStatus.INITIATED || state.recordedAlreadyGuestCheckoutWithStatus(guestStayId, CheckoutStatus.FAILED))
      return List.of();

    var guestCheckoutFailed = new GuestCheckoutFailureRecorded(
      state.id(),
      guestStayId,
      now
    );

    state = evolve(state, guestCheckoutFailed);

    return state.areAnyOngoingCheckouts() ?
      List.of(guestCheckoutFailed)
      : List.of(guestCheckoutFailed, finalize(state, now));
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


  public static List<GroupCheckoutEvent> decide(GroupCheckoutCommand command, GroupCheckout state) {
    return switch (command) {
      case InitiateGroupCheckout initiateGroupCheckout ->
        List.of(handle(initiateGroupCheckout));
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
