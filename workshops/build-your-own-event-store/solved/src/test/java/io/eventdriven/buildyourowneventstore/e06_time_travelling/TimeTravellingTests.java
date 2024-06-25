package io.eventdriven.buildyourowneventstore.e06_time_travelling;

import bankaccounts.BankAccount;
import io.eventdriven.buildyourowneventstore.tools.PostgresTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static bankaccounts.BankAccount.Event.*;
import static bankaccounts.BankAccountService.getBankAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TimeTravellingTests extends PostgresTest {
    @Test
    public void aggregateStream_ShouldReturnSpecifiedVersionOfTheStream() {
        var bankAccountId = UUID.randomUUID();
        var accountNumber = "PL61 1090 1014 0000 0712 1981 2874";
        var clientId = UUID.randomUUID();
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

        var cashierId = UUID.randomUUID();
        var depositRecorded = new DepositRecorded(bankAccountId, 100, cashierId, LocalDateTime.now(), ++version);

        var atmId = UUID.randomUUID();
        var cashWithdrawn = new CashWithdrawnFromATM(bankAccountId, 50, atmId, LocalDateTime.now(), ++version);

        eventStore.appendEvents(
            BankAccount.class,
            bankAccountId,
            bankAccountCreated, depositRecorded, cashWithdrawn
        );

        var aggregateAtVersion1 =
            getBankAccount(eventStore, bankAccountId, 0L, null).get();

        assertEquals(
            new BankAccount(bankAccountId, BankAccount.BankAccountStatus.Opened, 0, 0),
            aggregateAtVersion1
        );

        var aggregateAtVersion2 =
            getBankAccount(eventStore, bankAccountId, 1L, null).get();

        assertEquals(
            new BankAccount(bankAccountId, BankAccount.BankAccountStatus.Opened, depositRecorded.amount(), 1),
            aggregateAtVersion2
        );

        var aggregateAtVersion3 =
            getBankAccount(eventStore, bankAccountId, 2L, null).get();

        assertEquals(
            new BankAccount(bankAccountId, BankAccount.BankAccountStatus.Opened, depositRecorded.amount() - cashWithdrawn.amount(), 2),
            aggregateAtVersion3
        );
    }
}
