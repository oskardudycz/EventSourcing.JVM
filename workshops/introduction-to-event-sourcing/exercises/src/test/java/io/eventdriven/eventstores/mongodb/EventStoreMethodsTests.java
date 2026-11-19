package io.eventdriven.eventstores.mongodb;

import static io.eventdriven.eventstores.testing.bankaccounts.BankAccount.Event.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.eventstores.StreamName;
import io.eventdriven.eventstores.mongodb.events.EventEnvelope;
import io.eventdriven.eventstores.mongodb.subscriptions.BatchingPolicy;
import io.eventdriven.eventstores.mongodb.subscriptions.EventSubscriptionSettings;
import io.eventdriven.eventstores.testing.bankaccounts.BankAccount;
import io.eventdriven.eventstores.testing.tools.mongodb.MongoDBTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EventStoreMethodsTests extends MongoDBTest {
    @ParameterizedTest
    @MethodSource("mongoEventStorages")
    public void getEvents_ShouldReturnAppendedEvents(MongoDBEventStore.Storage storage)
            throws ExecutionException, InterruptedException, TimeoutException {
        var eventStore = getMongoEventStoreWith(storage);
        var now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

        var bankAccountId = UUID.randomUUID().toString();
        var accountNumber = "PL61 1090 1014 0000 0712 1981 2874";
        var clientId = UUID.randomUUID().toString();
        var currencyISOCOde = "PLN";
        var version = 0;

        var bankAccountCreated =
                new BankAccountOpened(bankAccountId, accountNumber, clientId, currencyISOCOde, now, version);

        var cashierId = UUID.randomUUID().toString();
        var depositRecorded = new DepositRecorded(bankAccountId, 100, cashierId, now, ++version);

        var atmId = UUID.randomUUID().toString();
        var cashWithdrawn = new CashWithdrawnFromATM(bankAccountId, 50, atmId, now, ++version);

        var streamName = StreamName.of(BankAccount.class, bankAccountId);

        var eventsFuture = new CompletableFuture<List<EventEnvelope>>();

        var settings = EventSubscriptionSettings.get()
                .filterWithStreamType(BankAccount.class)
                .handleBatch(eventsFuture::complete, BatchingPolicy.of(6, Duration.ofSeconds(5)));

        try (var _ = eventStore.subscribe(settings)) {
            eventStore.appendToStream(streamName, List.of(bankAccountCreated, depositRecorded, cashWithdrawn));

            eventStore.appendToStream(streamName, List.of(bankAccountCreated, depositRecorded, cashWithdrawn));

            var result = eventStore.readStream(streamName);
            var events = result.events();

            var updateChange = eventsFuture.get(5, TimeUnit.SECONDS);

            assertThat(updateChange.size()).isEqualTo(6);
            assertThat(events.size()).isEqualTo(6);

            assertThat(findFirstOfType(BankAccountOpened.class, events)).isEqualTo(bankAccountCreated);
            assertThat(findFirstOfType(DepositRecorded.class, events)).isEqualTo(depositRecorded);
            assertThat(findFirstOfType(CashWithdrawnFromATM.class, events)).isEqualTo(cashWithdrawn);
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
