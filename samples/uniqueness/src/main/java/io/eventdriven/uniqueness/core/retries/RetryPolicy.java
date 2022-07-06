package io.eventdriven.uniqueness.core.retries;

import io.eventdriven.uniqueness.core.processing.HandlerWithAck;

public interface RetryPolicy {
  <Result> Result run(HandlerWithAck<Result> perform);
}
