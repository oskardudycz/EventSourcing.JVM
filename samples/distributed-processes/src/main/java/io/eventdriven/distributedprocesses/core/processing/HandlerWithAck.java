package io.eventdriven.distributedprocesses.core.processing;

import java.util.function.Consumer;

@FunctionalInterface
public interface HandlerWithAck<Result> extends Consumer<Consumer<Result>> {

}
