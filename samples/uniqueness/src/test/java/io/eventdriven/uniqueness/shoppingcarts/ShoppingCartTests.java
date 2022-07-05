package io.eventdriven.uniqueness.shoppingcarts;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import com.eventstore.dbclient.ParseError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

public class ShoppingCartTests {

  @Test
  public void ShouldProvide() throws ExecutionException, InterruptedException {

  }

  private EventStoreDBClient eventStore;

  @BeforeEach
  void beforeEach() throws ParseError {
    EventStoreDBClientSettings settings = EventStoreDBConnectionString.parse("esdb://localhost:2113?tls=false");
    this.eventStore = EventStoreDBClient.create(settings);
  }
}
