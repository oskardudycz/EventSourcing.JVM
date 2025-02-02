package io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EventCatcher {
  private final List<Object> published = new ArrayList<>();

  public List<Object> getPublished() {
    return published;
  }

  public void catchEvent(Object event) {
    published.add(event);
  }

  public void reset() {
    published.clear();
  }

  public void shouldNotReceiveAnyEvent() {
    assertThat(published).isEmpty();
  }

  public <T> void shouldReceiveSingleEvent(T event) {
    assertThat(published).hasSize(1);
    assertThat(published)
      .filteredOn(e -> event.getClass().isInstance(e))
      .hasSize(1);
    assertThat(published.get(0)).isEqualTo(event);
  }
}
