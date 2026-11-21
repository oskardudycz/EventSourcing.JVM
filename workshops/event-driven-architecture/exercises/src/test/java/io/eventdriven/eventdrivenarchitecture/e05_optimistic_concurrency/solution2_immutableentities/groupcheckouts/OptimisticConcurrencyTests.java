package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts.GroupCheckoutEvent.GroupCheckoutCompleted;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts.GroupCheckoutEvent.GroupCheckoutFailed;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts.GroupCheckoutEvent.GroupCheckoutInitiated;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts.GroupCheckoutEvent.GuestCheckoutCompletionRecorded;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts.GroupCheckoutEvent.GuestCheckoutFailureRecorded;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class OptimisticConcurrencyTests {
    @Test
    @Tag("Exercise")
    public void groupCheckoutInitiatedConcurrently_IsInitiatedOnce() {
        // Given
        var groupCheckoutId = UUID.randomUUID();
        var clerkId = UUID.randomUUID();
        var guestStays = new UUID[] {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        var command = new InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now);

        // When
        inParallel(IntStream.range(0, 10)
                .<Runnable>mapToObj(i -> () -> groupCheckoutFacade.initiateGroupCheckout(command))
                .toList());

        // Then
        publishedMessages.shouldReceiveSingleMessage(
                new GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now));
    }

    @Test
    @Tag("Exercise")
    public void guestCheckoutCompletionsRecordedConcurrently_CompleteGroupCheckout() {
        // Given
        var guestStays = randomIds(10);
        var groupCheckoutId = initiateGroupCheckout(guestStays);

        // When
        inParallel(Arrays.stream(guestStays)
                .<Runnable>map(guestStayAccountId -> () -> groupCheckoutFacade.recordGuestCheckoutCompletion(
                        new RecordGuestCheckoutCompletion(groupCheckoutId, guestStayAccountId, now)))
                .toList());

        // Then
        var expected = new ArrayList<Object>(Arrays.stream(guestStays)
                .map(guestStayAccountId ->
                        new GuestCheckoutCompletionRecorded(groupCheckoutId, guestStayAccountId, now))
                .toList());
        expected.add(new GroupCheckoutCompleted(groupCheckoutId, guestStays, now));

        publishedMessages.shouldReceiveMessagesInAnyOrder(expected);
    }

    @Test
    @Tag("Exercise")
    public void guestCheckoutCompletionsAndFailureRecordedConcurrently_FailGroupCheckout() {
        // Given
        var guestStays = randomIds(10);
        var failedGuestStay = guestStays[0];
        var completedGuestStays = Arrays.copyOfRange(guestStays, 1, guestStays.length);
        var groupCheckoutId = initiateGroupCheckout(guestStays);

        // When
        inParallel(Stream.concat(
                        Stream.<Runnable>of(() -> groupCheckoutFacade.recordGuestCheckoutFailure(
                                new RecordGuestCheckoutFailure(groupCheckoutId, failedGuestStay, now))),
                        Arrays.stream(completedGuestStays)
                                .<Runnable>map(
                                        guestStayAccountId -> () -> groupCheckoutFacade.recordGuestCheckoutCompletion(
                                                new RecordGuestCheckoutCompletion(
                                                        groupCheckoutId, guestStayAccountId, now))))
                .toList());

        // Then
        var expected = new ArrayList<Object>();
        expected.add(new GuestCheckoutFailureRecorded(groupCheckoutId, failedGuestStay, now));
        Arrays.stream(completedGuestStays)
                .map(guestStayAccountId ->
                        new GuestCheckoutCompletionRecorded(groupCheckoutId, guestStayAccountId, now))
                .forEach(expected::add);
        expected.add(new GroupCheckoutFailed(groupCheckoutId, completedGuestStays, new UUID[] {failedGuestStay}, now));

        publishedMessages.shouldReceiveMessagesInAnyOrder(expected);
    }

    private MessageCatcher publishedMessages;
    private GroupCheckoutFacade groupCheckoutFacade;
    private OffsetDateTime now;

    @BeforeEach
    public void setUp() {
        Database database = new Database();
        MessageBus messageBus = new InMemoryMessageBus();
        publishedMessages = new MessageCatcher();
        groupCheckoutFacade = new GroupCheckoutFacade(database.collection(GroupCheckout.class), messageBus);
        now = OffsetDateTime.now();

        messageBus.use(publishedMessages::catchMessage);
    }

    private UUID initiateGroupCheckout(UUID[] guestStays) {
        var groupCheckoutId = UUID.randomUUID();
        groupCheckoutFacade.initiateGroupCheckout(
                new InitiateGroupCheckout(groupCheckoutId, UUID.randomUUID(), guestStays, now.minusDays(1)));
        publishedMessages.reset();

        return groupCheckoutId;
    }

    private static UUID[] randomIds(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toArray(UUID[]::new);
    }

    private static void inParallel(List<Runnable> handlers) {
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
                    throw new RuntimeException(e.getCause());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
