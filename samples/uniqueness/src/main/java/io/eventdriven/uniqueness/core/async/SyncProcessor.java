package io.eventdriven.uniqueness.core.async;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class SyncProcessor {
  public static <Result> Result runSync(Consumer<Consumer<Result>> perform) throws InterruptedException {
    final var blocker = new LinkedBlockingQueue<Result>();

    perform.accept(blocker::offer);
    // Now block until response or timeout
    return blocker.poll(10, TimeUnit.SECONDS);
  }
}
