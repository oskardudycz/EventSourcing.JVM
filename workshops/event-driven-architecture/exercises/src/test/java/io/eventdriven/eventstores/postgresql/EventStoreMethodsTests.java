package io.eventdriven.eventstores.postgresql;

import io.eventdriven.eventstores.EventStore;
import io.eventdriven.eventstores.StreamName;
import io.eventdriven.eventstores.testing.bankaccounts.BankAccount;
import io.eventdriven.eventstores.testing.tools.postgresql.PostgresTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.eventstores.testing.bankaccounts.BankAccount.Event.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventStoreMethodsTests extends PostgresTest {
  protected static EventStore eventStore;

  @BeforeAll
  public void setup() {
    // Create Event Store
    eventStore = new PostgreSQLEventStore(dbConnection);

    // Initialize Event Store
    eventStore.init();
  }

  @Test
  public void getEvents_ShouldReturnAppendedEvents() {
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

    eventStore.appendToStream(
      streamName,
      List.of(bankAccountCreated, depositRecorded, cashWithdrawn)
    );

    var result = eventStore.readStream(streamName);
    var events = result.events();

    assertEquals(3, events.size());

    assertEquals(bankAccountCreated, findFirstOfType(BankAccountOpened.class, events));
    assertEquals(depositRecorded, findFirstOfType(DepositRecorded.class, events));
    assertEquals(cashWithdrawn, findFirstOfType(CashWithdrawnFromATM.class, events));
  }

  private <Event> Event findFirstOfType(Class<Event> type, List<Object> events) {
    return events.stream()
      .filter(type::isInstance)
      .map(sc -> (Event) sc)
      .findFirst()
      .get();
  }
}
