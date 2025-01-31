package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.stream_as_document;

import bankaccounts.BankAccount;
import com.mongodb.client.MongoDatabase;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.StreamName;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventEnvelope;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions.BatchingPolicy;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions.EventSubscriptionSettings;
import io.eventdriven.buildyourowneventstore.tools.mongodb.MongoDBTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static bankaccounts.BankAccount.Event.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventStoreMethodsTests extends MongoDBTest {
  protected static MongoDBEventStore eventStore;
  protected MongoDatabase mongoDatabase;

  @BeforeAll
  public void setup() {
    // Create Event Store
    mongoDatabase = getFreshDatabase();
    eventStore = new MongoDBEventStore(mongoClient, mongoDatabase.getName());

    // Initialize Event Store
    eventStore.init();
  }

  @Test
  public void getEvents_ShouldReturnAppendedEvents() throws ExecutionException, InterruptedException, TimeoutException {
    var now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

    var bankAccountId = UUID.randomUUID().toString();
    var accountNumber = "PL61 1090 1014 0000 0712 1981 2874";
    var clientId = UUID.randomUUID().toString();
    var currencyISOCOde = "PLN";
    var version = 0;

    var bankAccountCreated = new BankAccountOpened(
      bankAccountId,
      accountNumber,
      clientId,
      currencyISOCOde,
      now,
      version
    );

    var cashierId = UUID.randomUUID().toString();
    var depositRecorded = new DepositRecorded(bankAccountId, 100, cashierId, now, ++version);

    var atmId = UUID.randomUUID().toString();
    var cashWithdrawn = new CashWithdrawnFromATM(bankAccountId, 50, atmId, now, ++version);

    var streamName = StreamName.of(BankAccount.class, bankAccountId);

    var eventsFuture = new CompletableFuture<List<EventEnvelope>>();

    var settings = EventSubscriptionSettings.get()
      .filterWithStreamType(BankAccount.class)
      .handleBatch(eventsFuture::complete, BatchingPolicy.ofSize(6));

    try (var _ = eventStore.subscribe(settings)) {
      eventStore.appendEvents(
        streamName,
        bankAccountCreated, depositRecorded, cashWithdrawn
      );

      eventStore.appendEvents(
        streamName,
        bankAccountCreated, depositRecorded, cashWithdrawn
      );

      var events = eventStore.getEvents(streamName);

      var updateChange = eventsFuture.get(5, TimeUnit.SECONDS);

      assertEquals(6, updateChange.size());
      assertEquals(6, events.size());

      assertEquals(bankAccountCreated, findFirstOfType(BankAccountOpened.class, events));
      assertEquals(depositRecorded, findFirstOfType(DepositRecorded.class, events));
      assertEquals(cashWithdrawn, findFirstOfType(CashWithdrawnFromATM.class, events));
    }
  }

  private <Event> Event findFirstOfType(Class<Event> type, List<Object> events) {
    return events.stream()
      .filter(type::isInstance)
      .map(sc -> (Event) sc)
      .findFirst()
      .get();
  }
}
