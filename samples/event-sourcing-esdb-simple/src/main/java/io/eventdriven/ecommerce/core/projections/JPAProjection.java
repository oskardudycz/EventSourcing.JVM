package io.eventdriven.ecommerce.core.projections;

import io.eventdriven.ecommerce.core.events.EventEnvelope;
import io.eventdriven.ecommerce.core.events.IEventHandler;
import io.eventdriven.ecommerce.core.views.VersionedView;
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
      var result = handle.apply(event);

      if(result instanceof VersionedView versionedView){
        versionedView.setMetadata(event.metadata());
      }

      repository.save(result);
    });
  }

  public static <TView, TId, TEvent> JPAProjection<TEvent> Update(
    Class<TEvent> type,
    JpaRepository<TView, TId> repository,
    Function<TEvent, TId> getId,
    BiFunction<TView, EventEnvelope<TEvent>, TView> handle
  ) {
    return new JPAProjection<>(type, event -> {
      var viewId = getId.apply(event.data());
      var view = repository.findById(viewId);

      if (view.isEmpty()) {
        System.out.println("View with id %s was not found for event %s".formatted(viewId, type.getName()));
        return;
      }

      if (view.get() instanceof VersionedView versionedView && wasAlreadyApplied(versionedView, event)) {
        System.out.println("View with id %s was not found for event %s".formatted(viewId, type.getName()));
        return;
      }

      var result = handle.apply(view.get(), event);

      if(result instanceof VersionedView versionedView){
        versionedView.setMetadata(event.metadata());
      }

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

  private static boolean wasAlreadyApplied(VersionedView view, EventEnvelope<?> eventEnvelope) {
    return view.getLastProcessedPosition() >= eventEnvelope.metadata().logPosition();
  }
}
