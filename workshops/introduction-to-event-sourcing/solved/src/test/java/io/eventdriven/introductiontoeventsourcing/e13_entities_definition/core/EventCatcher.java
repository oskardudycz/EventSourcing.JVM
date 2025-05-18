package io.eventdriven.introductiontoeventsourcing.e13_entities_definition.core;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventCatcher {
  public List<Object> published = new ArrayList<>();

  public void catchMessage(Object event) {
    published.add(event);
  }

  public void reset() {
    published.clear();
  }

  public void shouldNotReceiveAnyEvent(){
    assertThat(published).isEmpty();
  }

  public <Event> void shouldReceiveSingleEvent(Event event)
  {
    assertThat(published).hasSize(1);
    assertThat(published).hasOnlyElementsOfTypes(event.getClass()).hasSize(1);
    assertEquals(event, published.getFirst());
  }
}
