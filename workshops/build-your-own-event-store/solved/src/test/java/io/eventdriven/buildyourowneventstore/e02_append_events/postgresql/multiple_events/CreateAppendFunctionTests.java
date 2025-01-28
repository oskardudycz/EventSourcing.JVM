package io.eventdriven.buildyourowneventstore.e02_append_events.postgresql.multiple_events;

import bankaccounts.BankAccount;
import io.eventdriven.buildyourowneventstore.e02_append_events.EventStore;
import io.eventdriven.buildyourowneventstore.tools.PostgresTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static bankaccounts.BankAccount.Event.BankAccountOpened;
import static io.eventdriven.buildyourowneventstore.tools.SqlInvoker.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateAppendFunctionTests extends PostgresTest {
  protected static EventStore eventStore;

  @BeforeAll
  public void setup() {
    // Create Event Store
    eventStore = new PostgreSQLEventStore(dbConnection);

    // Initialize Event Store
    eventStore.init();
  }

  private final String appendEventFunctionName = "append_event";

  @Test
  public void appendEventFunction_WhenStreamDoesNotExist_CreateNewStream_And_AppendNewEvent() {
    var bankAccountId = UUID.randomUUID().toString();
    var accountNumber = "PL61 1090 1014 0000 0712 1981 2874";
    var clientId = UUID.randomUUID().toString();
    var currencyISOCOde = "PLN";

    var event = new BankAccountOpened(
      bankAccountId,
      accountNumber,
      clientId,
      currencyISOCOde,
      LocalDateTime.now(),
      1
    );

    eventStore.appendEvents(BankAccount.class, bankAccountId, event);

    var wasStreamCreated = querySingleSql(
      dbConnection,
      "select exists (select 1 as exist from streams where id = ?) as exist",
      setStringParam(bankAccountId),
      rs -> getBoolean(rs, "exist")
    );
    assertTrue(wasStreamCreated);

    var wasEventAppended = querySingleSql(
      dbConnection,
      "select exists (select 1 from events where stream_id = ?) as exist",
      setStringParam(bankAccountId),
      rs -> getBoolean(rs, "exist")
    );
    assertTrue(wasEventAppended);
  }
}
