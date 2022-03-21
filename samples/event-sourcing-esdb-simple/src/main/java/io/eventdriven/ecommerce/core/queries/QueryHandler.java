package io.eventdriven.ecommerce.core.queries;

@FunctionalInterface
public interface QueryHandler<TQuery, TResult> {
  TResult handle(TQuery command);
}
