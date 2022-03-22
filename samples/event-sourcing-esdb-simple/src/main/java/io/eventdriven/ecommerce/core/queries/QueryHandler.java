package io.eventdriven.ecommerce.core.queries;

@FunctionalInterface
public interface QueryHandler<Query, Result> {
  Result handle(Query command);
}
