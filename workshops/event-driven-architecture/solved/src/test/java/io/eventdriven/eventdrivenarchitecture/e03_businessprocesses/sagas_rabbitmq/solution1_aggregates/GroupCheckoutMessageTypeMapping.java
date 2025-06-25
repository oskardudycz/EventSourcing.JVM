package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning.MessageMapping;
import org.springframework.stereotype.Component;

@Component
public class GroupCheckoutMessageTypeMapping extends MessageMapping {
  public GroupCheckoutMessageTypeMapping() {
    // TODO: Register message type mappings for this module
    // Example:
    // register(GuestCheckoutInitiated.class,
    //   "checkout-initiated",                    // Current production name
    //   "GuestCheckoutStarted",                  // Old name (if renamed)
    //   "io.old.pkg.GuestCheckoutInitiated");   // Old package (if refactored)
    //
    // register(GuestCheckoutCompleted.class, "checkout-completed");
    // register(GuestCheckoutFailed.class, "checkout-failed");
  }
}
