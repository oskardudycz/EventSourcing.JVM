package io.eventdriven.uniqueness.core.retries;

import java.util.function.Consumer;

public class NulloRetryPolicy implements RetryPolicy {
  @Override
  public <Result> Result run(Consumer<Consumer<Result>> perform) {
    var wrapper = new Object() {
      Result result = null;
    };

    perform.accept(result -> {
      wrapper.result = result;
    });

    return wrapper.result;
  }
}
