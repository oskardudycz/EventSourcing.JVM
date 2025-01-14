package bankaccounts;

import io.eventdriven.buildyourowneventstore.EventStore;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public final class BankAccountService {
    public static Optional<BankAccount> getBankAccount(
        EventStore eventStore,
        UUID streamId
    ) {
        return getBankAccount(eventStore, streamId, null, null);
    }

    public static Optional<BankAccount> getBankAccount(
        EventStore eventStore,
        UUID streamId,
        Long atStreamVersion,
        LocalDateTime atTimestamp
    ) {
        return eventStore.aggregateStream(
            () -> new BankAccount(null, null, 0, -1),
            BankAccount::evolve,
            streamId,
            atStreamVersion,
            atTimestamp
        );
    }

}
