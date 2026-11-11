package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.InMemoryMessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageCatcher;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.groupcheckouts.GroupCheckout;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.groupcheckouts.GroupCheckoutEvent.GroupCheckoutCompleted;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.groupcheckouts.GroupCheckoutEvent.GroupCheckoutFailed;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.groupcheckouts.GroupCheckoutEvent.GroupCheckoutInitiated;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.groupcheckouts.GroupCheckoutEvent.GuestCheckoutCompletionRecorded;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.groupcheckouts.GroupCheckoutEvent.GuestCheckoutFailureRecorded;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.groupcheckouts.GroupCheckoutFacade;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.gueststayaccounts.GuestStayAccount;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.ChargeRecorded;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.GuestCheckedOut;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.GuestCheckoutFailed;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade.GuestStayAccountCommand.*;
import static org.assertj.core.api.Assertions.assertThat;

public class OptimisticConcurrencyTests {
  private MessageCatcher publishedMessages;
  private GuestStayAccountFacade guestStayFacade;
  private GroupCheckoutFacade groupCheckoutFacade;
  private OffsetDateTime now;

  @BeforeEach
  public void setUp() {
    Database database = new Database();
    MessageBus messageBus = new InMemoryMessageBus();
    publishedMessages = new MessageCatcher();
    guestStayFacade = new GuestStayAccountFacade(database.collection(GuestStayAccount.class), messageBus);
    groupCheckoutFacade = new GroupCheckoutFacade(database.collection(GroupCheckout.class), messageBus);
    now = OffsetDateTime.now();

    messageBus.use(publishedMessages::catchMessage);
  }

  @Test
  @Tag("Exercise")
  public void groupCheckoutInitiatedConcurrently_IsInitiatedOnce() {
    var groupCheckoutId = UUID.randomUUID();
    var clerkId = UUID.randomUUID();
    var guestStays = new UUID[]{UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
    var command = new InitiateGroupCheckout(groupCheckoutId, clerkId, guestStays, now);

    inParallel(
      IntStream.range(0, 5)
        .<Runnable>mapToObj(i -> () -> groupCheckoutFacade.initiateGroupCheckout(command))
        .toList()
    );

    publishedMessages.shouldReceiveSingleMessage(
      new GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStays, now)
    );
  }

  @Test
  @Tag("Exercise")
  public void guestCheckoutCompletionsRecordedConcurrently_CompleteGroupCheckout() {
    var guestStays = randomIds(5);
    var groupCheckoutId = initiateGroupCheckout(guestStays);

    inParallel(
      Arrays.stream(guestStays)
        .<Runnable>map(guestStayId -> () -> groupCheckoutFacade.recordGuestCheckoutCompletion(
          new RecordGuestCheckoutCompletion(groupCheckoutId, guestStayId, now)
        ))
        .toList()
    );

    var expected = new ArrayList<Object>(
      Arrays.stream(guestStays)
        .map(guestStayId -> new GuestCheckoutCompletionRecorded(groupCheckoutId, guestStayId, now))
        .toList()
    );
    expected.add(new GroupCheckoutCompleted(groupCheckoutId, guestStays, now));

    publishedMessages.shouldReceiveMessagesInAnyOrder(expected);
  }

  @Test
  @Tag("Exercise")
  public void guestCheckoutCompletionsAndFailureRecordedConcurrently_FailGroupCheckout() {
    var guestStays = randomIds(5);
    var failedGuestStay = guestStays[0];
    var completedGuestStays = Arrays.copyOfRange(guestStays, 1, guestStays.length);
    var groupCheckoutId = initiateGroupCheckout(guestStays);

    inParallel(
      Stream.concat(
        Stream.<Runnable>of(() -> groupCheckoutFacade.recordGuestCheckoutFailure(
          new RecordGuestCheckoutFailure(groupCheckoutId, failedGuestStay, now)
        )),
        Arrays.stream(completedGuestStays)
          .<Runnable>map(guestStayId -> () -> groupCheckoutFacade.recordGuestCheckoutCompletion(
            new RecordGuestCheckoutCompletion(groupCheckoutId, guestStayId, now)
          ))
      ).toList()
    );

    var expected = new ArrayList<Object>();
    expected.add(new GuestCheckoutFailureRecorded(groupCheckoutId, failedGuestStay, now));
    Arrays.stream(completedGuestStays)
      .map(guestStayId -> new GuestCheckoutCompletionRecorded(groupCheckoutId, guestStayId, now))
      .forEach(expected::add);
    expected.add(new GroupCheckoutFailed(groupCheckoutId, completedGuestStays, new UUID[]{failedGuestStay}, now));

    publishedMessages.shouldReceiveMessagesInAnyOrder(expected);
  }

  @Test
  @Tag("Exercise")
  public void chargesRecordedConcurrently_AllCountTowardsBalance() {
    var guestStayId = checkInGuest();
    var amounts = List.of(10.0, 20.0, 30.0, 40.0, 50.0);

    inParallel(
      amounts.stream()
        .<Runnable>map(amount -> () -> guestStayFacade.recordCharge(new RecordCharge(guestStayId, UUID.randomUUID(), amount, now)))
        .toList()
    );
    guestStayFacade.recordPayment(
      new RecordPayment(guestStayId, UUID.randomUUID(), amounts.stream().mapToDouble(Double::doubleValue).sum(), now)
    );
    publishedMessages.reset();

    guestStayFacade.checkOutGuest(new CheckOutGuest(guestStayId, now));

    publishedMessages.shouldReceiveSingleMessage(new GuestCheckedOut(guestStayId, now, null));
  }

  @Test
  @Tag("Exercise")
  public void chargeRecordedConcurrentlyWithCheckout_DoesNotLetGuestCheckOutWithUnsettledBalance() {
    var guestStayId = checkInGuest();

    inParallel(
      List.of(
        () -> guestStayFacade.checkOutGuest(new CheckOutGuest(guestStayId, now)),
        () -> guestStayFacade.recordCharge(new RecordCharge(guestStayId, UUID.randomUUID(), 100, now))
      ),
      RuntimeException.class
    );

    var publishedTypes = publishedMessages.published.stream()
      .map(message -> message.getClass().getSimpleName())
      .sorted()
      .toList();
    var checkedOutBeforeCharge = List.of(GuestCheckedOut.class.getSimpleName());
    var chargedBeforeCheckout = Stream.of(ChargeRecorded.class, GuestCheckoutFailed.class)
      .map(Class::getSimpleName)
      .sorted()
      .toList();

    assertThat(publishedTypes).isIn(checkedOutBeforeCharge, chargedBeforeCheckout);
  }

  private UUID initiateGroupCheckout(UUID[] guestStays) {
    var groupCheckoutId = UUID.randomUUID();
    groupCheckoutFacade.initiateGroupCheckout(
      new InitiateGroupCheckout(groupCheckoutId, UUID.randomUUID(), guestStays, now.minusDays(1))
    );
    publishedMessages.reset();

    return groupCheckoutId;
  }

  private UUID checkInGuest() {
    var guestStayId = UUID.randomUUID();
    guestStayFacade.checkInGuest(new CheckInGuest(guestStayId, now.minusDays(1)));
    publishedMessages.reset();

    return guestStayId;
  }

  private static UUID[] randomIds(int count) {
    return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toArray(UUID[]::new);
  }

  private static void inParallel(List<Runnable> handlers) {
    inParallel(handlers, null);
  }

  private static void inParallel(List<Runnable> handlers, Class<? extends Exception> ignoring) {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var results = handlers.stream().map(executor::submit).toList();

      for (var result : results) {
        try {
          result.get();
        } catch (ExecutionException e) {
          if (e.getCause().getClass() != ignoring)
            throw new RuntimeException(e.getCause());
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }
}
