package io.eventdriven.talk;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import com.eventstore.dbclient.ParseError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StreamTransformationsTests {

  @Test
  public void test() {
    assertEquals(true, true);
  }

  private EventStoreDBClient eventStore;

  @BeforeEach
  void beforeEach() throws ParseError {
    var settings = EventStoreDBConnectionString.parse("esdb://localhost:2113?tls=false");
    this.eventStore = EventStoreDBClient.create(settings);
  }
}
