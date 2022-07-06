package io.eventdriven.uniqueness.core.processing;

import java.util.function.Consumer;

@FunctionalInterface
public interface HandlerWithAck<Result> extends Consumer<Consumer<Result>> {

}
