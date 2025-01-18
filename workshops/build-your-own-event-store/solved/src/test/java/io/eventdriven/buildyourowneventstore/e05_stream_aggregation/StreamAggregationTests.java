package io.eventdriven.buildyourowneventstore.e05_stream_aggregation;

import bankaccounts.BankAccount;
import io.eventdriven.buildyourowneventstore.EventStore;
import io.eventdriven.buildyourowneventstore.PgEventStore;
import io.eventdriven.buildyourowneventstore.tools.PostgresTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static bankaccounts.BankAccount.Event.*;
import static bankaccounts.BankAccountService.getBankAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StreamAggregationTests extends PostgresTest {
  protected static EventStore eventStore;

  @BeforeAll
  public void setup() {
    // Create Event Store
    eventStore = new PgEventStore(dbConnection);

    // Initialize Event Store
    eventStore.init();
  }

  @Test
  public void aggregateStream_ShouldReturnObjectWithStateBasedOnEvents() {
    var bankAccountId = UUID.randomUUID().toString();
    var accountNumber = "PL61 1090 1014 0000 0712 1981 2874";
    var clientId = UUID.randomUUID().toString();
    var currencyISOCOde = "PLN";
    var createdAt = LocalDateTime.now();
    var version = 0;

    var bankAccountCreated = new BankAccountOpened(
      bankAccountId,
      accountNumber,
      clientId,
      currencyISOCOde,
      createdAt,
      version
    );

    var cashierId = UUID.randomUUID().toString();
    var depositRecorded = new DepositRecorded(bankAccountId, 100, cashierId, LocalDateTime.now(), ++version);

    var atmId = UUID.randomUUID().toString();
    var cashWithdrawn = new CashWithdrawnFromATM(bankAccountId, 50, atmId, LocalDateTime.now(), ++version);

    eventStore.appendEvents(
      BankAccount.class,
      bankAccountId,
      bankAccountCreated, depositRecorded, cashWithdrawn
    );

    var bankAccount = getBankAccount(eventStore, bankAccountId);

    assertTrue(bankAccount.isPresent());
    assertEquals(
      new BankAccount(bankAccountId, BankAccount.BankAccountStatus.Opened, 50, 2),
      bankAccount.get()
    );
  }
}
