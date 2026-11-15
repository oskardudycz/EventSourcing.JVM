package io.eventdriven.distributedprocesses.hotelmanagement.processmanager.gueststayaccount;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.events.EventBus;
import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.distributedprocesses.hotelmanagement.processmanager.gueststayaccount.GuestStayAccountCommand.*;
import org.springframework.core.retry.RetryTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

public class GuestStayAccountService {
  private final AggregateStore<GuestStayAccount, GuestStayAccountEvent, UUID> store;
  private final EventBus eventBus;
  private final RetryTemplate retryTemplate;

  public GuestStayAccountService(
    AggregateStore<GuestStayAccount, GuestStayAccountEvent, UUID> store,
    EventBus eventBus,
    RetryTemplate retryTemplate
  ) {
    this.store = store;
    this.eventBus = eventBus;
    this.retryTemplate = retryTemplate;
  }

  public ETag handle(CheckInGuest command) {
    return store.getAndUpdate(
      command.guestStayAccountId(),
      current -> current.open(
        command.guestStayAccountId(),
        OffsetDateTime.now()
      )
    );
  }

  public ETag handle(RecordCharge command) {
    return store.getAndUpdate(
      command.guestStayAccountId(),
      current -> current.recordCharge(
        command.amount(),
        OffsetDateTime.now()
      )
    );
  }

  public ETag handle(RecordPayment command) {
    return store.getAndUpdate(
      command.guestStayAccountId(),
      current -> current.recordPayment(
        command.amount(),
        OffsetDateTime.now()
      )
    );
  }

  public ETag handle(CheckOutGuest command) {
    return retryTemplate.invoke(() ->
      store.getAndUpdate(
        command.guestStayAccountId(),
        current -> current.checkout(
          command.guestStayAccountId(),
          OffsetDateTime.now()
        )
      )
    );
  }
}

