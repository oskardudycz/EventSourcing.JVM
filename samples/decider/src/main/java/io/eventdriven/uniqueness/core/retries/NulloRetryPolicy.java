package io.eventdriven.uniqueness.core.retries;

import io.eventdriven.uniqueness.core.processing.HandlerWithAck;
import io.eventdriven.uniqueness.core.processing.HandlerWithAckProcessor;

public class NulloRetryPolicy implements RetryPolicy {
  @Override
  public <Result> Result run(HandlerWithAck<Result> perform) {
    var result = HandlerWithAckProcessor.run(perform);

    return result.orElse(null);
  }
}
