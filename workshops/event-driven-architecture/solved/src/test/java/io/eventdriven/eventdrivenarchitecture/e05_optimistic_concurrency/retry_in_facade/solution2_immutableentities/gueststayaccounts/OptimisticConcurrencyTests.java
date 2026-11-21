package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution2_immutableentities.gueststayaccounts;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.ChargeRecorded;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.GuestCheckedIn;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.GuestCheckedOut;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.GuestCheckoutFailed;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OptimisticConcurrencyTests {
    @Test
    public void guestCheckedInConcurrently_IsCheckedInOnce() {
        // Given
        var guestStayId = UUID.randomUUID();
        var command = new CheckInGuest(guestStayId, now);

        // When
        inParallel(IntStream.range(0, 10)
                .<Runnable>mapToObj(i -> () -> guestStayFacade.checkInGuest(command))
                .toList());

        // Then
        publishedMessages.shouldReceiveSingleMessage(new GuestCheckedIn(guestStayId, now));
    }

    @Test
    public void chargesRecordedConcurrently_AllCountTowardsBalance() {
        // Given
        var guestStayId = checkInGuest();
        var amounts = List.of(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0);

        // When
        inParallel(amounts.stream()
                .<Runnable>map(amount -> () ->
                        guestStayFacade.recordCharge(new RecordCharge(guestStayId, UUID.randomUUID(), amount, now)))
                .toList());

        // Then
        guestStayFacade.recordPayment(new RecordPayment(
                guestStayId,
                UUID.randomUUID(),
                amounts.stream().mapToDouble(Double::doubleValue).sum(),
                now));
        publishedMessages.reset();

        guestStayFacade.checkOutGuest(new CheckOutGuest(guestStayId, now));

        publishedMessages.shouldReceiveSingleMessage(new GuestCheckedOut(guestStayId, now, null));
    }

    @Test
    public void paymentsRecordedConcurrently_AllCountTowardsBalance() {
        // Given
        var guestStayId = checkInGuest();
        var amounts = List.of(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0);
        guestStayFacade.recordCharge(new RecordCharge(
                guestStayId,
                UUID.randomUUID(),
                amounts.stream().mapToDouble(Double::doubleValue).sum(),
                now));

        // When
        inParallel(amounts.stream()
                .<Runnable>map(amount -> () ->
                        guestStayFacade.recordPayment(new RecordPayment(guestStayId, UUID.randomUUID(), amount, now)))
                .toList());

        // Then
        publishedMessages.reset();

        guestStayFacade.checkOutGuest(new CheckOutGuest(guestStayId, now));

        publishedMessages.shouldReceiveSingleMessage(new GuestCheckedOut(guestStayId, now, null));
    }

    @Test
    public void chargeRecordedConcurrentlyWithCheckout_DoesNotLetGuestCheckOutWithUnsettledBalance() {
        // Given
        var guestStayIds = IntStream.range(0, 10).mapToObj(i -> checkInGuest()).toList();

        // When
        inParallel(
                guestStayIds.stream()
                        .flatMap(guestStayId -> Stream.<Runnable>of(
                                () -> guestStayFacade.checkOutGuest(new CheckOutGuest(guestStayId, now)),
                                () -> guestStayFacade.recordCharge(
                                        new RecordCharge(guestStayId, UUID.randomUUID(), 100, now))))
                        .toList(),
                RuntimeException.class);

        // Then
        var checkedOutBeforeCharge = List.of(GuestCheckedOut.class.getSimpleName());
        var chargedBeforeCheckout = Stream.of(ChargeRecorded.class, GuestCheckoutFailed.class)
                .map(Class::getSimpleName)
                .sorted()
                .toList();
        for (var guestStayId : guestStayIds) {
            var publishedTypes = publishedMessages.published.stream()
                    .filter(message -> guestStayIdOf(message).equals(guestStayId))
                    .map(message -> message.getClass().getSimpleName())
                    .sorted()
                    .toList();

            assertThat(publishedTypes).isIn(checkedOutBeforeCharge, chargedBeforeCheckout);
        }
    }

    private MessageCatcher publishedMessages;
    private GuestStayAccountFacade guestStayFacade;
    private OffsetDateTime now;

    @BeforeEach
    public void setUp() {
        Database database = new Database();
        MessageBus messageBus = new InMemoryMessageBus();
        publishedMessages = new MessageCatcher();
        guestStayFacade = new GuestStayAccountFacade(database.collection(GuestStayAccount.class), messageBus);
        now = OffsetDateTime.now();

        messageBus.use(publishedMessages::catchMessage);
    }

    private UUID checkInGuest() {
        var guestStayId = UUID.randomUUID();
        guestStayFacade.checkInGuest(new CheckInGuest(guestStayId, now.minusDays(1)));
        publishedMessages.reset();

        return guestStayId;
    }

    private static UUID guestStayIdOf(Object message) {
        return switch ((GuestStayAccountEvent) message) {
            case ChargeRecorded event -> event.guestStayAccountId();
            case GuestCheckedOut event -> event.guestStayAccountId();
            case GuestCheckoutFailed event -> event.guestStayAccountId();
            default -> throw new IllegalStateException("Unexpected message: " + message);
        };
    }

    private static void inParallel(List<Runnable> handlers) {
        inParallel(handlers, null);
    }

    private static void inParallel(List<Runnable> handlers, Class<? extends Exception> ignoring) {
        var startGate = new CyclicBarrier(handlers.size());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = handlers.stream()
                    .map(handler -> executor.submit(() -> {
                        startGate.await();
                        handler.run();
                        return null;
                    }))
                    .toList();

            for (var result : results) {
                try {
                    result.get();
                } catch (ExecutionException e) {
                    if (e.getCause().getClass() != ignoring) throw new RuntimeException(e.getCause());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
