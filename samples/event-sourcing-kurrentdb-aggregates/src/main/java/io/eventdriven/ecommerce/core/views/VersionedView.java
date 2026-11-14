package io.eventdriven.ecommerce.core.views;

import io.eventdriven.ecommerce.core.events.EventMetadata;

public interface VersionedView {
  long getLastProcessedPosition();

  void setMetadata(EventMetadata eventMetadata);
}
