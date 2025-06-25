package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution1_aggregates;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.versioning.MessageMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GroupCheckoutMessageTypeMapping {

  @Bean
  public MessageMapping groupCheckoutMessageMapping() {
    return new MessageMapping();
    // TODO: Register message type mappings and transformations for this module
    // Example for current schema:
    //   .register("checkout-initiated", GuestCheckoutInitiated.class)
    //   .register("checkout-completed", GuestCheckoutCompleted.class)
    //
    // Example for old schema transformation:
    //   .register(
    //     "checkout-started-v1",
    //     OldGuestCheckoutStarted.class,
    //     old -> new GuestCheckoutInitiated(old.id(), old.timestamp())
    //   )
  }
}
