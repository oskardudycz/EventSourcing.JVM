package io.eventdriven.hotelmanagement.saga.gueststayaccount;

import io.eventdriven.core.entities.EntityStore;
import io.eventdriven.core.http.ETag;
import io.eventdriven.hotelmanagement.saga.gueststayaccount.GuestStayAccountCommand.*;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.retry.RetryTemplate;

public class GuestStayAccountService {
    private final EntityStore<GuestStayAccount, GuestStayAccountEvent> store;
    private RetryTemplate retryTemplate;

    public GuestStayAccountService(
            EntityStore<GuestStayAccount, GuestStayAccountEvent> store, RetryTemplate retryTemplate) {
        this.store = store;
        this.retryTemplate = retryTemplate;
    }

    public ETag handle(CheckInGuest command) {
        return handle(command.guestStayAccountId(), command)
                .orElseThrow(() -> new IllegalStateException("Invalid state"));
    }

    public ETag handle(RecordCharge command) {
        return handle(command.guestStayAccountId(), command)
                .orElseThrow(() -> new IllegalStateException("Invalid state"));
    }

    public ETag handle(RecordPayment command) {
        return handle(command.guestStayAccountId(), command)
                .orElseThrow(() -> new IllegalStateException("Invalid state"));
    }

    public Optional<ETag> handle(CheckOutGuest command) {
        return retryTemplate.invoke(() -> handle(command.guestStayAccountId(), command));
    }

    private Optional<ETag> handle(UUID id, GuestStayAccountCommand command) {
        return store.getAndUpdate((state) -> GuestStayAccountDecider.handle(command, state), id);
    }
}
