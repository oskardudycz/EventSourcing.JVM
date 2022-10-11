package io.eventdriven.buildyourowneventstore.e04_event_store_methods;

import bankaccounts.BankAccount;
import io.eventdriven.buildyourowneventstore.tools.PostgresTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static bankaccounts.BankAccount.Event.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventStoreMethodsTests extends PostgresTest {
    @Test
    public void getEvents_ShouldReturnAppendedEvents() {
        var now = LocalDateTime.now();

        var bankAccountId = UUID.randomUUID();
        var accountNumber = "PL61 1090 1014 0000 0712 1981 2874";
        var clientId = UUID.randomUUID();
        var currencyISOCOde = "PLN";
        var version = 1;

        var bankAccountCreated = new BankAccountOpened(
            bankAccountId,
            accountNumber,
            clientId,
            currencyISOCOde,
            now,
            version
        );

        var cashierId = UUID.randomUUID();
        var depositRecorded = new DepositRecorded(bankAccountId, 100, cashierId, now, ++version);

        var atmId = UUID.randomUUID();
        var cashWithdrawn = new CashWithdrawnFromATM(bankAccountId, 50, atmId, now, ++version);

        eventStore.appendEvents(
            BankAccount.class,
            bankAccountId,
            bankAccountCreated, depositRecorded, cashWithdrawn
        );

        var events = eventStore.getEvents(bankAccountId);

        assertEquals(3, events.size());

        assertEquals(bankAccountCreated, findFirstOfType(BankAccountOpened.class, events));
        assertEquals(depositRecorded, findFirstOfType(DepositRecorded.class, events));
        assertEquals(cashWithdrawn, findFirstOfType(CashWithdrawnFromATM.class, events));
    }

    private <Event> Event findFirstOfType(Class<Event> type, List<Object> events) {
        return events.stream()
            .filter(sc -> type.isInstance(sc))
            .map(sc -> (Event) sc)
            .findFirst()
            .get();
    }
}
