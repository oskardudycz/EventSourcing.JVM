package io.eventdriven.uniqueness.core.retries;

import java.util.function.Consumer;

public interface RetryPolicy {
  <Result> Result run(Consumer<Consumer<Result>> perform);
}
