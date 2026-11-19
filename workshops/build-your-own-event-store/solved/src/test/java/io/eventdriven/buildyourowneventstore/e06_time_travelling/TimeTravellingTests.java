package io.eventdriven.buildyourowneventstore.e06_time_travelling;

import static bankaccounts.BankAccount.Event.*;
import static bankaccounts.BankAccountService.getBankAccount;
import static org.assertj.core.api.Assertions.assertThat;

import bankaccounts.BankAccount;
import io.eventdriven.buildyourowneventstore.EventStore;
import io.eventdriven.buildyourowneventstore.PgEventStore;
import io.eventdriven.buildyourowneventstore.tools.PostgresTest;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TimeTravellingTests extends PostgresTest {
    protected static EventStore eventStore;

    @BeforeAll
    public void setup() {
        // Create Event Store
        eventStore = new PgEventStore(dbConnection);

        // Initialize Event Store
        eventStore.init();
    }

    @Test
    public void aggregateStream_ShouldReturnSpecifiedVersionOfTheStream() {
        var bankAccountId = UUID.randomUUID().toString();
        var accountNumber = "PL61 1090 1014 0000 0712 1981 2874";
        var clientId = UUID.randomUUID().toString();
        var currencyISOCOde = "PLN";
        var createdAt = LocalDateTime.now();
        var version = 0;

        var bankAccountCreated =
                new BankAccountOpened(bankAccountId, accountNumber, clientId, currencyISOCOde, createdAt, version);

        var cashierId = UUID.randomUUID().toString();
        var depositRecorded = new DepositRecorded(bankAccountId, 100, cashierId, LocalDateTime.now(), ++version);

        var atmId = UUID.randomUUID().toString();
        var cashWithdrawn = new CashWithdrawnFromATM(bankAccountId, 50, atmId, LocalDateTime.now(), ++version);

        eventStore.appendEvents(BankAccount.class, bankAccountId, bankAccountCreated, depositRecorded, cashWithdrawn);

        var aggregateAtVersion1 =
                getBankAccount(eventStore, bankAccountId, 0L, null).get();

        assertThat(aggregateAtVersion1)
                .isEqualTo(new BankAccount(bankAccountId, BankAccount.BankAccountStatus.Opened, 0, 0));

        var aggregateAtVersion2 =
                getBankAccount(eventStore, bankAccountId, 1L, null).get();

        assertThat(aggregateAtVersion2)
                .isEqualTo(new BankAccount(
                        bankAccountId, BankAccount.BankAccountStatus.Opened, depositRecorded.amount(), 1));

        var aggregateAtVersion3 =
                getBankAccount(eventStore, bankAccountId, 2L, null).get();

        assertThat(aggregateAtVersion3)
                .isEqualTo(new BankAccount(
                        bankAccountId,
                        BankAccount.BankAccountStatus.Opened,
                        depositRecorded.amount() - cashWithdrawn.amount(),
                        2));
    }
}
