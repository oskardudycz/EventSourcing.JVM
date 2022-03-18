package io.eventdriven.ecommerce.core.projections;

import io.eventdriven.ecommerce.core.events.EventEnvelope;
import io.eventdriven.ecommerce.core.events.IEventHandler;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class JPAProjection<TEvent> implements IEventHandler<TEvent> {
  private final Consumer<EventEnvelope<TEvent>> handle;
  private final Class<TEvent> eventType;

  private JPAProjection(Class<TEvent> type, Consumer<EventEnvelope<TEvent>> handle) {
    this.eventType = type;
    this.handle = handle;
  }

  public static <TView, TEvent> JPAProjection<TEvent> Add(Class<TEvent> type, JpaRepository<TView, ?> repository, Function<EventEnvelope<TEvent>, TView> handle) {
    return new JPAProjection<>(type, event -> {
      repository.save(
        handle.apply(event)
      );
    });
  }

  public static <TView, TId, TEvent> JPAProjection<TEvent> Update(
    Class<TEvent> type,
    JpaRepository<TView, TId> repository,
    Function<TEvent, TId> getId,
    BiFunction<TView, EventEnvelope<TEvent>, TView> handle
  ) {
    return new JPAProjection<>(type, event -> {
      var view = repository.findById(getId.apply(event.data()));

      if (view.isEmpty())
        return;

      repository.save(
        handle.apply(view.get(), event)
      );
    });
  }

  @Override
  public Class<TEvent> getEventType() {
    return eventType;
  }

  @Override
  public void handle(EventEnvelope<TEvent> event) {
    this.handle.accept(event);
  }
}
