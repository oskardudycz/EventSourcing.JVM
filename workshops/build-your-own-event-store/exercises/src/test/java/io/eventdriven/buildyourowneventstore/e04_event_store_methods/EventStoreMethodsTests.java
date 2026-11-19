package io.eventdriven.buildyourowneventstore.e04_event_store_methods;

import static bankaccounts.BankAccount.Event.*;
import static org.assertj.core.api.Assertions.assertThat;

import bankaccounts.BankAccount;
import io.eventdriven.buildyourowneventstore.EventStore;
import io.eventdriven.buildyourowneventstore.PgEventStore;
import io.eventdriven.buildyourowneventstore.tools.PostgresTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class EventStoreMethodsTests extends PostgresTest {
    protected static EventStore eventStore;

    @BeforeAll
    public void setup() {
        // Create Event Store
        eventStore = new PgEventStore(dbConnection);

        // Initialize Event Store
        eventStore.init();
    }

    @Test
    public void getEvents_ShouldReturnAppendedEvents() {
        var now = LocalDateTime.now();

        var bankAccountId = UUID.randomUUID();
        var accountNumber = "PL61 1090 1014 0000 0712 1981 2874";
        var clientId = UUID.randomUUID();
        var currencyISOCOde = "PLN";
        var version = 0;

        var bankAccountCreated =
                new BankAccountOpened(bankAccountId, accountNumber, clientId, currencyISOCOde, now, version);

        var cashierId = UUID.randomUUID();
        var depositRecorded = new DepositRecorded(bankAccountId, 100, cashierId, now, ++version);

        var atmId = UUID.randomUUID();
        var cashWithdrawn = new CashWithdrawnFromATM(bankAccountId, 50, atmId, now, ++version);

        eventStore.appendEvents(BankAccount.class, bankAccountId, bankAccountCreated, depositRecorded, cashWithdrawn);

        var events = eventStore.getEvents(bankAccountId);

        assertThat(events.size()).isEqualTo(3);

        assertThat(findFirstOfType(BankAccountOpened.class, events)).isEqualTo(bankAccountCreated);
        assertThat(findFirstOfType(DepositRecorded.class, events)).isEqualTo(depositRecorded);
        assertThat(findFirstOfType(CashWithdrawnFromATM.class, events)).isEqualTo(cashWithdrawn);
    }

    private <Event> Event findFirstOfType(Class<Event> type, List<Object> events) {
        return events.stream()
                .filter(type::isInstance)
                .map(sc -> (Event) sc)
                .findFirst()
                .get();
    }
}
