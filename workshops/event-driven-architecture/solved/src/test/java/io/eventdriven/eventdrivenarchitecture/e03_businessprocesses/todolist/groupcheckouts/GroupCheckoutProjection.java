package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.core.Projection;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckout.CheckoutStatus.COMPLETED;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckout.CheckoutStatus.FAILED;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckoutEvent.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountEvent.GuestCheckedOut;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountEvent.GuestCheckoutFailed;

public class GroupCheckoutProjection extends Projection<GroupCheckout> {
  public GroupCheckoutProjection(Database.Collection<GroupCheckout> collection) {
    super(collection);
  }

  @Override
  public List<Class<?>> canHandle() {
    return List.of(
      GroupCheckoutInitiated.class,
      GuestCheckedOut.class,
      GuestCheckoutFailed.class,
      GroupCheckoutCompleted.class,
      GroupCheckoutFailed.class
    );
  }

  @Override
  protected Optional<UUID> getDocumentId(Object event) {
    return switch (event) {
      case GroupCheckoutInitiated e -> Optional.of(e.groupCheckoutId());
      case GuestCheckedOut e -> Optional.ofNullable(e.groupCheckoutId());
      case GuestCheckoutFailed e -> Optional.ofNullable(e.groupCheckoutId());
      case GroupCheckoutCompleted e -> Optional.of(e.groupCheckoutId());
      case GroupCheckoutFailed e -> Optional.of(e.groupCheckoutId());
      default -> Optional.empty();
    };
  }

  @Override
  protected @Nullable GroupCheckout evolve(@Nullable GroupCheckout current, Object event) {
    return switch (event) {
      case GroupCheckoutInitiated e -> GroupCheckout.initiated(e.groupCheckoutId(), e.guestStayAccountIds(), e.initiatedAt());
      case GuestCheckedOut e -> existing(current).withCheckout(e.guestStayAccountId(), COMPLETED, e.completedAt());
      case GuestCheckoutFailed e -> existing(current).withCheckout(e.guestStayAccountId(), FAILED, e.failedAt());
      case GroupCheckoutCompleted _, GroupCheckoutFailed _ -> null;
      default -> current;
    };
  }

  private static GroupCheckout existing(@Nullable GroupCheckout current) {
    if (current == null)
      throw new IllegalStateException("Entity not found");

    return current;
  }
}
