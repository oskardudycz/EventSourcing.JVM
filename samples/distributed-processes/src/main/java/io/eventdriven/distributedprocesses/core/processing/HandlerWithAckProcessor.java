package io.eventdriven.distributedprocesses.core.processing;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class HandlerWithAckProcessor {
  public static <Result> Optional<Result> run(HandlerWithAck<Result> handler){
    var reference = new AtomicReference<Optional<Result>>();

    handler.accept(result -> reference.set(Optional.of(result)));

    return reference.get();
  }
}
