package io.eventdriven.ecommerce.core.subscriptions;

import java.time.LocalDateTime;

record CheckpointStored(
  String subscriptionId,
  long position,
  LocalDateTime checkpointedAt
) {
}
