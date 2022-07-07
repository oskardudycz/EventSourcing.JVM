package io.eventdriven.distributedprocesses.core.async;

import io.eventdriven.distributedprocesses.core.processing.HandlerWithAck;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class SyncProcessor {
  public static <Result> Result runSync(HandlerWithAck<Result> perform) throws InterruptedException {
    final var blocker = new LinkedBlockingQueue<Result>();

    perform.accept(blocker::offer);
    // Now block until response or timeout
    return blocker.poll(10, TimeUnit.SECONDS);
  }
}
