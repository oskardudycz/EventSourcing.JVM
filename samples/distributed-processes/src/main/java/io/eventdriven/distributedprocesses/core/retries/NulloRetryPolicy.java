package io.eventdriven.distributedprocesses.core.retries;

import io.eventdriven.distributedprocesses.core.processing.HandlerWithAck;
import io.eventdriven.distributedprocesses.core.processing.HandlerWithAckProcessor;

public class NulloRetryPolicy implements RetryPolicy {
  @Override
  public <Result> Result run(HandlerWithAck<Result> perform) {
    var result = HandlerWithAckProcessor.run(perform);

    return result.orElse(null);
  }
}
