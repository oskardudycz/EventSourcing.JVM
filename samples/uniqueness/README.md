[![Twitter Follow](https://img.shields.io/twitter/follow/oskar_at_net?style=social)](https://twitter.com/oskar_at_net) [![Github Sponsors](https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&link=https://github.com/sponsors/oskardudycz/)](https://github.com/sponsors/oskardudycz/)  ![Github Actions](https://github.com/oskardudycz/EventSourcing.JVM/actions/workflows/samples_event-sourcing-esdb-simple.yml/badge.svg?branch=main) [![blog](https://img.shields.io/badge/blog-event--driven.io-brightgreen)](https://event-driven.io/?utm_source=event_sourcing_jvm) [![blog](https://img.shields.io/badge/%F0%9F%9A%80-Architecture%20Weekly-important)](https://www.architecture-weekly.com/?utm_source=event_sourcing_jvm) 
# Event Schema Versioning

- [Event Schema Versioning](#event-schema-versioning)
  - [Enforcing unique constraint by stream id](#enforcing-unique-constraint-by-stream-id)
  - [Revoking unique value by stream id](#revoking-unique-value-by-stream-id)
  - [Reservation pattern](#reservation-pattern)

Uniqueness constraint is one of the most common requests but also (surprisingly) the most challenging. Typically it means that the business tries to bring us a potential solution for their problem instead of explaining the root issue to us. We should always ask enough whys before diving into a technical solution. Read more in my article [Bring me problems, not solutions!](https://event-driven.io/en/uniqueness-in-event-sourcing/?utm_source=event_sourcing_jvm).

Moreover, the uniqueness constraint in the real world is a thing. Typically duplicates may appear, and then we're doing compensating operations or merging them. Implementing such compensation may be much easier than sophisticated technical solutions to guarantee uniqueness if that rarely happens.

Yet, sometimes we need to cut discussions and just do the work. Some time ago, I wrote an article [How to ensure uniqueness in Event Sourcing](https://event-driven.io/en/uniqueness-in-event-sourcing/?utm_source=event_sourcing_jvm). It explains all the most popular techniques for dealing with this case. I suggest reading it before going to code. These samples are an appendix showing how to do it practically.

Have a look also at the article [Tell, don't ask! Or, how to keep an eye on boiling milk](https://event-driven.io/en/tell_dont_ask_how_to_keep_an_eye_on_boiling_milk/?utm_source=event_sourcing_jvm). I explained why querying for the read model is never a bulletproof solution; it can be, at best, good enough.

## Enforcing unique constraint by stream id

Event stores are types of [key-value stores](https://event-driven.io/pl/key-value-stores/?utm_source=event_sourcing_jvm). They guarantee the uniqueness of the key. We can use that to guarantee the uniqueness of our data. We can add unique data as part of the stream id. It works well for use cases where unique fields do not change once they're set up. If they change, we'd need to create a new stream, deleting the old one. For instance, we'd like to enforce only a single shopping cart for the client. We could do that by having the following stream id pattern:

```
var shoppingCartStreamId = "shopping_cart-%s".formatted(clientId);
```

Then we can tell the event store that we expect the stream not to exist. For example, implementation using EventStoreDB will look as follows:

```java
var clientId = UUID.randomUUID();
// We're assuming that there can be only a single shopping cart open for specific client.
// We can enforce uniqueness by putting client id into a stream id
var shoppingCartStreamId = "shopping_cart-%s".formatted(clientId);
var shoppingCartOpened = new ShoppingCartOpened(clientId, clientId);

// This one should succeed as we don't have such stream yet
eventStore.appendToStream(
  shoppingCartStreamId,
  AppendToStreamOptions.get().expectedRevision(ExpectedRevision.NO_STREAM),
  EventSerializer.serialize(shoppingCartOpened)
).get();

// This one will fail, as we're expecting that stream doesn't exist
try {
  eventStore.appendToStream(
    shoppingCartStreamId,
  AppendToStreamOptions.get().expectedRevision(ExpectedRevision.NO_STREAM),
    EventSerializer.serialize(shoppingCartOpened)
  ).get();
} catch (ExecutionException exception) {
  assertInstanceOf(WrongExpectedVersionException.class, exception.getCause());
}
```

See more in [ShoppingCartTests](./src/test/java/io/eventdriven/uniqueness/shoppingcarts/ShoppingCartTests.java).

We could also use stream id to enforce the uniqueness of multiple keys. For instance, cinema ticket reservations should be unique for the specific screening and seat id. We could either create a conjoined stream id or use some [decent hash](./src/main/java/io/eventdriven/uniqueness/core/resourcereservation/Hash.java). We can combine all the values into the string and hash them, e.g.:

```java
var seatReservationId = "cinema_ticket-%s".formatted(
  Hash.hash("%s_%s".formatted(screeningId, seatId)).toString()
);
```
See more in [CinemaTicketTests](./src/test/java/io/eventdriven/uniqueness/cinematickets/CinemaTicketTests.java).

This technique could also be useful for GDPR data, like user email:

```java
var userId = Hash.hash(email).toString();
```

## Revoking unique value by stream id

Sometimes we need to revoke the unique value, e.g. someone cancelled a seat reservation. In EventStoreDB, we can use soft delete to mark stream events as _to-be-delted_but will allow registering it again. It will cause us to reuse the reservation stream for potentially multiple tickets. If we add new events to a soft-deleted stream, and they were not [scavenged](https://developers.eventstore.com/server/v21.10/operations.html#scavenging-events), then those events will reappear in the stream. That's why we should add a _tobstone event_ that will mark where the previous reservation finished. We could subscribe and delete the stream once such an event was appended.

See more in [CinemaTicketTests](./src/test/java/io/eventdriven/uniqueness/cinematickets/CinemaTicketTests.java).

## Reservation pattern

For more advanced scenarios, the Reservation pattern comes to the rescue. When performing a business operation, first, we request a resource reservation: e.g. a unique email value. Reservation should be durable and respected by concurrent resources. Typically it's recorded in some durable storage. For instance, for key/value storage like Redis, we may use the unique resource id (e.g. user email) as a key.

Most importantly, this storage should allow us to claim the resource with a unique constraint. The reservation can be synchronous or asynchronous (e.g. when it requires more business logic than just adding an entry in some database). We can continue our main business logic only after confirmation that the reservation was successful. 

With a reserved resource (e.g. user email), we can run the rest of the business logic and store the results in our main data storage.

Implementation of the reservation pattern in EventStoreDB could look like this:

```java
public class ESDBResourceReservationHandler implements ResourceReservationHandler {
  private static final Logger logger = LoggerFactory.getLogger(ESDBResourceReservationHandler.class);
  private final Duration reservationLockDuration;
  private final EventStore eventStore;
  private final RetryPolicy retryPolicy;

  public ESDBResourceReservationHandler(
    Duration reservationLockDuration,
    RetryPolicy retryPolicy,
    EventStore eventStore
  ) {
    this.reservationLockDuration = reservationLockDuration;
    this.eventStore = eventStore;
    this.retryPolicy = retryPolicy;
  }

  @Override
  public Boolean reserve(String resourceKey, HandlerWithAck<Boolean> onReserved) {
    try {
      final var reservationStreamId = streamName(resourceKey);

      var initiationResult = initiateReservation(
        resourceKey,
        reservationStreamId,
        reservationLockDuration
      );

      if (!(initiationResult instanceof EventStore.AppendResult.Success success)) {
        logger.error("Failed to reserve '%s'".formatted(reservationStreamId));
        return false;
      }


      var succeeded = run(onReserved).orElse(false);

      if (!succeeded) {
        markReservationAsReleased(resourceKey, reservationStreamId, success.nextExpectedRevision());
        return false;
      }

      var confirmationReservation = confirmReservation(
        resourceKey,
        reservationStreamId,
        success.nextExpectedRevision()
      );

      return confirmationReservation instanceof EventStore.AppendResult.Success;
    } catch (Throwable e) {
      logger.error("Error while reserving resource");
      return false;
    }
  }

  @Override
  public void release(String resourceKey) {
    var result = eventStore.deleteStream(streamName(resourceKey));

    if (result instanceof EventStore.DeleteResult.UnexpectedFailure) {
      throw new RuntimeException("Error while deleting stream: %s".formatted(result));
    }
  }

  private EventStore.AppendResult initiateReservation(String resourceKey, String reservationStreamId, Duration tentativeLockDuration) {
    final var reservationInitiated = new ResourceReservationInitiated(
      resourceKey,
      OffsetDateTime.now(),
      tentativeLockDuration
    );

    return retryPolicy.run(ack -> {
      var result = eventStore.append(reservationStreamId, reservationInitiated);

      if(!(result instanceof EventStore.AppendResult.UnexpectedFailure))
        ack.accept(result);
    });
  }

  private EventStore.AppendResult confirmReservation(String resourceKey, String reservationStreamId, StreamRevision expectedRevision) {
    final var reservationConfirmed = new ResourceReservationConfirmed(
      resourceKey,
      OffsetDateTime.now()
    );

    return retryPolicy.run(ack -> {
      var result = eventStore.append( reservationStreamId, expectedRevision, reservationConfirmed);

      if(!(result instanceof EventStore.AppendResult.UnexpectedFailure))
        ack.accept(result);
    });
  }

  private void markReservationAsReleased(String resourceKey, String reservationStreamId, StreamRevision expectedRevision) {
    // We're marking reservation as to be released instead of deleting stream.
    // That's needed as if we'd delete stream here, then we wouldn't get event notification through subscriptions.
    // Because of that we wouldn't be able to clear the lookup for timed out reservations.
    final var reservationReleased = new ResourceReservationReleaseInitiated(
      resourceKey,
      OffsetDateTime.now()
    );

    retryPolicy.run(ack -> {
      var result = eventStore.append(
        reservationStreamId,
        AppendToStreamOptions.get().expectedRevision(expectedRevision),
        EventSerializer.serialize(reservationReleased)
      );

      if(!(result instanceof EventStore.AppendResult.UnexpectedFailure))
        ack.accept(result);
    });
  }

  private static String streamName(String resourceKey){
    return "reservation-%s".formatted(resourceKey);
  }
}
```

See more in [ESDBResourceReservationHandler.java](./src/main/java/io/eventdriven/uniqueness/core/resourcereservation/esdb/ESDBResourceReservationHandler.java).

Yet, this is not enough. The logic may fail, or the process may die, and we could end up partially processed information. Primarily since EventStoreDB doesn't support transactions (only atomic appends of multiple events to the same stream). We need to ensure that we'll have failures compensated. To do that, we can store the current reservation data in external storage (e.g. relational DB, Redis, etc.). It will need to be held in other storage, as ESDB cannot: 
- set TTL for a single event (only for all stream events), 
- cannot send scheduled events, 
- cannot do filtered queries.

To fill the generic reservation read model, we can use the following event handler [ResourceReservationEventHandler.java](./src/main/java/io/eventdriven/uniqueness/core/resourcereservation/ResourceReservationEventHandler.java) together with a specific business event handler [UserEmailReservationEventHandler.java](./src/main/java/io/eventdriven/uniqueness/users/reservation/UserEmailReservationEventHandler.java). Those event handlers will ensure that we have the read model with information about the reservation.

When we have the read model updated, we can run the CRON job, which will clean up timed-out reservations. The scavenging logic can look as follows [ResourceReservationScavenging.java](./src/main/java/io/eventdriven/uniqueness/core/resourcereservation/jpa/ResourceReservationScavenging.java).

See also tests showing the example usage:
- fallback - [UserEmailRegistrationFallbackTests.java](./src/test/java/io/eventdriven/uniqueness/users/UserEmailRegistrationFallbackTests.java]
- scavenging:  [UserEmailRegistrationScavengingTests.java](./src/test/java/io/eventdriven/uniqueness/users/UserEmailRegistrationScavengingTests.java).

Still, the reservation pattern is a complicated process to operate and orchestrate. As I mentioned at the beginning, the best is to start understanding the problem we're trying to fix, then either compensate or use stream id for uniqueness. If that's not enough, then use Reservation Pattern. It's also worth evaluating other storage for the reservation process coordination, as they may have capabilities (e.g. transactions) to simplify it.