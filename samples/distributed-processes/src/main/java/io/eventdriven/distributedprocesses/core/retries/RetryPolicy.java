package io.eventdriven.distributedprocesses.core.retries;

import io.eventdriven.distributedprocesses.core.processing.HandlerWithAck;

public interface RetryPolicy {
  <Result> Result run(HandlerWithAck<Result> perform);
}
