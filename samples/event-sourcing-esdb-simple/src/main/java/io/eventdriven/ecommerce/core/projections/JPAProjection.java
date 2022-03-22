package io.eventdriven.ecommerce.core.projections;

import io.eventdriven.ecommerce.core.events.EventEnvelope;
import io.eventdriven.ecommerce.core.views.VersionedView;
import org.springframework.data.repository.CrudRepository;

import java.util.function.Function;
import java.util.function.Supplier;

public abstract class JPAProjection<View, Id> {
  private final CrudRepository<View, Id> repository;

  protected JPAProjection(CrudRepository<View, Id> repository) {
    this.repository = repository;
  }

  protected <Event> void Add(EventEnvelope<Event> eventEnvelope, Supplier<View> handle) {
    var result = handle.get();

    if(result instanceof VersionedView versionedView){
      versionedView.setMetadata(eventEnvelope.metadata());
    }

    repository.save(result);
  }

  protected <Event> void GetAndUpdate(
    Id viewId,
    EventEnvelope<Event> eventEnvelope,
    Function<View, View> handle
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

  protected void DeleteById(Id viewId) {
    repository.deleteById(viewId);
  }

  private static boolean wasAlreadyApplied(VersionedView view, EventEnvelope<?> eventEnvelope) {
    return view.getLastProcessedPosition() >= eventEnvelope.metadata().logPosition();
  }
}
