package io.eventdriven.ecommerce.core.projections;

import io.eventdriven.ecommerce.core.events.EventEnvelope;
import io.eventdriven.ecommerce.core.views.VersionedView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.function.Function;
import java.util.function.Supplier;

public abstract class JPAProjection<TView, TId> {
  private final JpaRepository<TView, TId> repository;

  protected JPAProjection(JpaRepository<TView, TId> repository) {
    this.repository = repository;
  }

  protected  <TEvent> void Add(EventEnvelope<TEvent> eventEnvelope, Supplier<TView> handle) {
    var result = handle.get();

    if(result instanceof VersionedView versionedView){
      versionedView.setMetadata(eventEnvelope.metadata());
    }

    repository.save(result);
  }

  protected <TEvent> void GetAndUpdate(
    TId viewId,
    EventEnvelope<TEvent> eventEnvelope,
    Function<TView, TView> handle
  ) {
    var view = repository.findById(viewId);

    if (view.isEmpty()) {
      System.out.println("View with id %s was not found for event %s".formatted(viewId, eventEnvelope.metadata().eventType()));
      return;
    }

    if (view.get() instanceof VersionedView versionedView && wasAlreadyApplied(versionedView, eventEnvelope)) {
      System.out.println("View with id %s was not found for event %s".formatted(viewId, eventEnvelope.metadata().eventType()));
      return;
    }

    var result = handle.apply(view.get());

    if(result instanceof VersionedView versionedView){
      versionedView.setMetadata(eventEnvelope.metadata());
    }

    repository.save(result);
  }

  private static boolean wasAlreadyApplied(VersionedView view, EventEnvelope<?> eventEnvelope) {
    return view.getLastProcessedPosition() >= eventEnvelope.metadata().logPosition();
  }
}
