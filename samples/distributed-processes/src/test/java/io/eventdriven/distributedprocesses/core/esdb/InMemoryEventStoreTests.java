package io.eventdriven.distributedprocesses.core.esdb;

import com.eventstore.dbclient.ExpectedRevision;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;

public class InMemoryEventStoreTests {
  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final ArrayList<String> log = new ArrayList<>();

  private static final String cartId = "cart-1";
  private static final String streamId = "ShoppingCart-cart-1";

  @Test
  public void appendStoresEventsThatCanBeReadBack() {
    var opened = new ShoppingCartOpened(cartId, openedAt());
    var added = new ProductItemAdded(cartId, "t-shirt");

    assertThat(eventStore.append(streamId, opened, added).succeeded()).isTrue();

    assertThat(eventsOf(eventStore.read(streamId))).containsExactly(opened, added);
  }

  @Test
  public void appendRoundTripsEventsThroughJsonInsteadOfKeepingTheInstances() {
    var opened = new ShoppingCartOpened(cartId, openedAt());

    eventStore.append(streamId, opened);

    var read = eventsOf(eventStore.read(streamId));

    assertThat(read).containsExactly(opened);
    assertThat(read[0]).isNotSameAs(opened);
  }

  @Test
  public void readOnUnknownStreamReturnsStreamDoesNotExist() {
    assertThat(eventStore.read("ShoppingCart-unknown"))
      .isInstanceOf(EventStore.ReadResult.StreamDoesNotExist.class);
  }

  @Test
  public void appendWithoutExpectedRevisionStartsStreamAtRevisionZero() {
    var result = eventStore.append(streamId, new ShoppingCartOpened(cartId, openedAt()));

    assertThat(result)
      .isEqualTo(new EventStore.AppendResult.Success(ExpectedRevision.expectedRevision(0), logPosition(0)));
  }

  @Test
  public void appendReturnsLogPositionOfTheLastAppendedEventAcrossAllStreams() {
    eventStore.append(streamId, new ShoppingCartOpened(cartId, openedAt()), new ProductItemAdded(cartId, "t-shirt"));

    var result = eventStore.append("ShoppingCart-cart-2", new ShoppingCartOpened("cart-2", openedAt()));

    assertThat(result)
      .isEqualTo(new EventStore.AppendResult.Success(ExpectedRevision.expectedRevision(0), logPosition(2)));
  }

  @Test
  public void subscribersGetTheStreamAndLogPositionOfEachEvent() {
    var metadata = new ArrayList<EventMetadata>();
    eventStore.subscribe(ShoppingCartOpened.class, envelope -> metadata.add(envelope.metadata()));
    eventStore.subscribe(ProductItemAdded.class, envelope -> metadata.add(envelope.metadata()));

    eventStore.append(streamId, new ShoppingCartOpened(cartId, openedAt()), new ProductItemAdded(cartId, "t-shirt"));
    eventStore.append("ShoppingCart-cart-2", new ShoppingCartOpened("cart-2", openedAt()));

    assertThat(metadata).containsExactly(
      new EventMetadata(streamId, 0, 0),
      new EventMetadata(streamId, 1, 1),
      new EventMetadata("ShoppingCart-cart-2", 0, 2)
    );
  }

  @Test
  public void appendReturnsRevisionOfTheLastAppendedEvent() {
    var result = eventStore.append(
      streamId,
      new ShoppingCartOpened(cartId, openedAt()),
      new ProductItemAdded(cartId, "t-shirt")
    );

    assertThat(nextExpectedRevisionOf(result)).isEqualTo(ExpectedRevision.expectedRevision(1));
  }

  @Test
  public void appendWithNoStreamOnExistingStreamReturnsStreamAlreadyExists() {
    eventStore.append(streamId, new ShoppingCartOpened(cartId, openedAt()));

    var result = eventStore.append(
      streamId,
      ExpectedRevision.noStream(),
      new ProductItemAdded(cartId, "t-shirt")
    );

    assertThat(result).isEqualTo(
      new EventStore.AppendResult.StreamAlreadyExists(ExpectedRevision.expectedRevision(0))
    );
  }

  @Test
  public void appendWithMatchingRevisionAppendsAndAdvancesRevision() {
    eventStore.append(streamId, new ShoppingCartOpened(cartId, openedAt()));

    var result = eventStore.append(
      streamId,
      ExpectedRevision.expectedRevision(0),
      new ProductItemAdded(cartId, "t-shirt")
    );

    assertThat(nextExpectedRevisionOf(result)).isEqualTo(ExpectedRevision.expectedRevision(1));
    assertThat(eventsOf(eventStore.read(streamId))).hasSize(2);
  }

  @Test
  public void appendWithNotMatchingRevisionReturnsConflictAndAppendsNothing() {
    eventStore.append(streamId, new ShoppingCartOpened(cartId, openedAt()));

    var result = eventStore.append(
      streamId,
      ExpectedRevision.expectedRevision(4),
      new ProductItemAdded(cartId, "t-shirt")
    );

    assertThat(result).isEqualTo(new EventStore.AppendResult.Conflict(
      ExpectedRevision.expectedRevision(4),
      ExpectedRevision.expectedRevision(0)
    ));
    assertThat(eventsOf(eventStore.read(streamId))).hasSize(1);
  }

  @Test
  public void appendWithAnyRevisionAlwaysAppends() {
    eventStore.append(streamId, ExpectedRevision.any(), new ShoppingCartOpened(cartId, openedAt()));

    var result = eventStore.append(
      streamId,
      ExpectedRevision.any(),
      new ProductItemAdded(cartId, "t-shirt")
    );

    assertThat(nextExpectedRevisionOf(result)).isEqualTo(ExpectedRevision.expectedRevision(1));
  }

  @Test
  public void appendWithStreamExistsNeedsAnExistingStream() {
    var onUnknownStream = eventStore.append(
      streamId,
      ExpectedRevision.streamExists(),
      new ProductItemAdded(cartId, "t-shirt")
    );

    assertThat(onUnknownStream).isEqualTo(new EventStore.AppendResult.Conflict(
      ExpectedRevision.streamExists(),
      ExpectedRevision.noStream()
    ));

    eventStore.append(streamId, new ShoppingCartOpened(cartId, openedAt()));

    var onExistingStream = eventStore.append(
      streamId,
      ExpectedRevision.streamExists(),
      new ProductItemAdded(cartId, "t-shirt")
    );

    assertThat(nextExpectedRevisionOf(onExistingStream)).isEqualTo(ExpectedRevision.expectedRevision(1));
  }

  @Test
  public void notifiesSubscribersOfAppendedEventsInOrder() {
    eventStore.subscribe(ShoppingCartOpened.class, envelope -> log.add("opened:" + envelope.data().cartId()));
    eventStore.subscribe(ProductItemAdded.class, envelope -> log.add("added:" + envelope.data().productId()));

    eventStore.append(
      streamId,
      new ShoppingCartOpened(cartId, openedAt()),
      new ProductItemAdded(cartId, "t-shirt")
    );

    assertThat(log).containsExactly("opened:cart-1", "added:t-shirt");
  }

  @Test
  public void runsMiddlewareForEveryEventBeforeItsSubscribers() {
    eventStore.use(message -> log.add("middleware:" + message.getClass().getSimpleName()));
    eventStore.subscribe(ShoppingCartOpened.class, envelope -> log.add("opened"));
    eventStore.subscribe(ProductItemAdded.class, envelope -> log.add("added"));

    eventStore.append(
      streamId,
      new ShoppingCartOpened(cartId, openedAt()),
      new ProductItemAdded(cartId, "t-shirt")
    );

    assertThat(log).containsExactly(
      "middleware:ShoppingCartOpened", "opened",
      "middleware:ProductItemAdded", "added"
    );
  }

  @Test
  public void notifiesAllSubscribersOfTheTypeInRegistrationOrder() {
    eventStore.subscribe(ShoppingCartOpened.class, envelope -> log.add("first"));
    eventStore.subscribe(ShoppingCartOpened.class, envelope -> log.add("second"));

    eventStore.append(streamId, new ShoppingCartOpened(cartId, openedAt()));

    assertThat(log).containsExactly("first", "second");
  }

  @Test
  public void notifiesByExactRuntimeClassNotByAssignability() {
    eventStore.subscribe(ShoppingCartEvent.class, envelope -> log.add("supertype"));

    eventStore.append(streamId, new ShoppingCartOpened(cartId, openedAt()));

    assertThat(log).isEmpty();
  }

  @Test
  public void failedAppendNotifiesNobody() {
    eventStore.append(streamId, new ShoppingCartOpened(cartId, openedAt()));

    eventStore.use(message -> log.add("middleware"));
    eventStore.subscribe(ProductItemAdded.class, envelope -> log.add("added"));

    var result = eventStore.append(
      streamId,
      ExpectedRevision.expectedRevision(4),
      new ProductItemAdded(cartId, "t-shirt")
    );

    assertThat(result.succeeded()).isFalse();
    assertThat(log).isEmpty();
  }

  @Test
  public void appendFromInsideASubscriberIsDeliveredAfterTheEventBeingHandled() {
    eventStore.subscribe(ShoppingCartOpened.class, envelope -> {
      log.add("opened:start");
      eventStore.append("ShoppingCart-cart-2", new ProductItemAdded("cart-2", "t-shirt"));
      log.add("opened:end");
    });
    eventStore.subscribe(ProductItemAdded.class, envelope -> log.add("added:" + envelope.data().cartId()));

    eventStore.append(
      streamId,
      new ShoppingCartOpened(cartId, openedAt()),
      new ProductItemAdded(cartId, "hoodie")
    );

    assertThat(log).containsExactly("opened:start", "opened:end", "added:cart-1", "added:cart-2");
  }

  @Test
  public void failingSubscriberDoesNotFailTheAppendNorStopOtherSubscribers() {
    eventStore.subscribe(ShoppingCartOpened.class, envelope -> {
      throw new IllegalStateException("projection is down");
    });
    eventStore.subscribe(ShoppingCartOpened.class, envelope -> log.add("other"));

    var result = eventStore.append(streamId, new ShoppingCartOpened(cartId, openedAt()));

    assertThat(result.succeeded()).isTrue();
    assertThat(log).containsExactly("other");
  }

  @Test
  public void failedEventIsDeliveredAgainOnTheNextAppendBeforeNewerEvents() {
    var attempts = new int[]{0};
    eventStore.subscribe(ProductItemAdded.class, envelope -> {
      if (attempts[0]++ == 0)
        throw new IllegalStateException("broker is down");

      log.add("added:" + envelope.data().productId());
    });

    eventStore.append(
      streamId,
      new ProductItemAdded(cartId, "hoodie"),
      new ProductItemAdded(cartId, "t-shirt")
    );

    assertThat(log).isEmpty();

    eventStore.append(streamId, ExpectedRevision.any(), new ProductItemAdded(cartId, "socks"));

    assertThat(log).containsExactly("added:hoodie", "added:t-shirt", "added:socks");
  }

  @Test
  public void subscriberAddedLaterCatchesUpFromTheStartOfTheLog() {
    eventStore.append(streamId, new ShoppingCartOpened(cartId, openedAt()));

    eventStore.subscribe(ShoppingCartOpened.class, envelope -> log.add("opened"));
    eventStore.subscribe(ProductItemAdded.class, envelope -> log.add("added"));

    eventStore.append(streamId, ExpectedRevision.any(), new ProductItemAdded(cartId, "t-shirt"));

    assertThat(log).containsExactly("opened", "added");
  }

  private static OffsetDateTime openedAt() {
    return OffsetDateTime.of(2024, 5, 1, 10, 15, 30, 0, ZoneOffset.ofHours(2));
  }

  private static Object[] eventsOf(EventStore.ReadResult result) {
    assertThat(result).isInstanceOf(EventStore.ReadResult.Success.class);

    return ((EventStore.ReadResult.Success) result).events();
  }

  private static ExpectedRevision nextExpectedRevisionOf(EventStore.AppendResult result) {
    assertThat(result).isInstanceOf(EventStore.AppendResult.Success.class);

    return ((EventStore.AppendResult.Success) result).nextExpectedRevision();
  }

  private static com.eventstore.dbclient.Position logPosition(long position) {
    return new com.eventstore.dbclient.Position(position, position);
  }

  sealed interface ShoppingCartEvent {
  }

  record ShoppingCartOpened(String cartId, OffsetDateTime openedAt) implements ShoppingCartEvent {
  }

  record ProductItemAdded(String cartId, String productId) implements ShoppingCartEvent {
  }
}
