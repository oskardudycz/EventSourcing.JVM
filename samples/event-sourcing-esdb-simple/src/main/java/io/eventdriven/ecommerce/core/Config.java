package io.eventdriven.ecommerce.core;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import com.eventstore.dbclient.ParseError;

public class Config {
  public static EventStoreDBClient eventStoreDBClient() throws ParseError {
    return eventStoreDBClient("esdb://localhost:2113?tls=false");
  }

  public static EventStoreDBClient eventStoreDBClient(String connectionString) throws ParseError {
    EventStoreDBClientSettings settings = EventStoreDBConnectionString.parse(connectionString);

    return EventStoreDBClient.create(settings);
  }
}
