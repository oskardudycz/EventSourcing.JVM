package io.eventdriven.eventstores.mongodb.event_as_document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import io.eventdriven.eventstores.StreamName;
import io.eventdriven.eventstores.mongodb.MongoDBEventStore;
import io.eventdriven.eventstores.mongodb.event_as_document.streams.EventStream;
import io.eventdriven.eventstores.mongodb.events.EventDataCodec;
import io.eventdriven.eventstores.mongodb.events.EventEnvelope;
import io.eventdriven.eventstores.mongodb.events.EventMetadata;
import io.eventdriven.eventstores.mongodb.events.EventTypeMapper;
import io.eventdriven.eventstores.mongodb.subscriptions.EventSubscription;
import io.eventdriven.eventstores.mongodb.subscriptions.EventSubscriptionSettings;
import io.eventdriven.eventstores.mongodb.subscriptions.MongoEventStreamCursor;
import org.bson.conversions.Bson;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class MongoDBEventStoreWithEventAsDocument implements MongoDBEventStore {
  private final MongoClient mongoClient;
  private final MongoDatabase database;
  private final EventDataCodec eventDataCodec;
  private final EventTypeMapper eventTypeMapper;

  public MongoDBEventStoreWithEventAsDocument(MongoClient mongoClient, String databaseName) {
    this.mongoClient = mongoClient;
    this.mongoClient.getDatabase(databaseName).drop();
    database = this.mongoClient.getDatabase(databaseName);
    eventTypeMapper = EventTypeMapper.DEFAULT;
    eventDataCodec = new EventDataCodec(mongoClient.getCodecRegistry(), eventTypeMapper);
  }

  @Override
  public void init() {
    database.createCollection("streams", new CreateCollectionOptions());
    database.createCollection("events", new CreateCollectionOptions());

    streamsCollection()
      .createIndex(
        Indexes.ascending("streamName"),
        new IndexOptions().unique(true)
      );

    eventsCollection()
      .createIndex(
        Indexes.ascending("metadata.streamName", "metadata.streamPosition"),
        new IndexOptions().unique(true)
      );
  }

  @Override
  public AppendResult appendToStream(StreamName streamName, Long expectedStreamPosition, Object... events) {
    var streamType = streamName.streamType();
    var streamId = streamName.streamId();
    var streamNameValue = streamName.toString();

    // Resolve collections
    var streamsCollection = streamsCollection();
    var eventsCollection = eventsCollection();

    var now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

    return mongoClient.startSession().withTransaction(() -> {
      long currentStreamPosition;
      if (expectedStreamPosition != null) {
        currentStreamPosition = expectedStreamPosition;
      } else {
        var stream = streamsCollection.find(Filters.eq("streamName", streamNameValue))
          .projection(Projections.include("metadata.streamPosition"))
          .first();

        currentStreamPosition = stream != null ?
          stream.metadata().streamPosition()
          : 0L;
      }

      var envelopes = IntStream.range(0, events.length)
        .mapToObj(index -> {
          var event = events[index];

          return EventEnvelope.of(
            event,
            new EventMetadata(
              UUID.randomUUID().toString(),
              eventTypeMapper.toName(event.getClass()),
              currentStreamPosition + index + 1,
              streamNameValue
            ),
            eventDataCodec
          );
        }).toList();

      // Append events upserting the document
      var streamUpdateResult = streamsCollection.updateOne(
        Filters.and(
          Filters.eq("streamName", streamNameValue),
          Filters.eq("metadata.streamPosition", currentStreamPosition)
        ),
        Updates.combine(
          // Append events
          // Increment stream position
          Updates.inc("metadata.streamPosition", events.length),
          // Set default metadata on insert
          Updates.setOnInsert("streamName", streamNameValue),
          Updates.setOnInsert("metadata.streamId", streamId),
          Updates.setOnInsert("metadata.streamType", streamType),
          Updates.setOnInsert("metadata.createdAt", now),
          // Update metadata
          Updates.set("metadata.updatedAt", now)
        ),
        upsert
      );

      if (streamUpdateResult.getModifiedCount() == 0L && streamUpdateResult.getUpsertedId() == null)
        throw new IllegalStateException("Expected version did not match the stream version!");

      var eventAppendResult = eventsCollection.insertMany(
        envelopes
      );

      if (eventAppendResult.getInsertedIds().size() != envelopes.size())
        throw new IllegalStateException("Expected version did not match the stream version!");

      return new AppendResult(currentStreamPosition + events.length);
    });
  }

  @Override
  public ReadStreamResult readStream(StreamName streamName) {
    var eventsCollection = eventsCollection();

    var stream = streamsCollection().find(Filters.eq("streamName", streamName.toString()))
      .projection(Projections.include("metadata.streamPosition"))
      .first();

    if(stream == null) {
      return new ReadStreamResult(0, List.of());
    }

    var events = eventsCollection
      .find(Filters.eq("metadata.streamName", streamName.toString()))
      .into(new ArrayList<>())
      .stream()
      .map(eventEnvelope -> eventEnvelope.getEvent(eventDataCodec))
      .toList();

    return new ReadStreamResult(stream.metadata().streamPosition(), events);
  }

  public EventSubscription subscribe(EventSubscriptionSettings settings) {
    var subscription = new EventSubscription(
      () -> MongoEventStreamCursor.from(
        eventsCollection(),
        filterSubscription(settings.streamType()),
        MongoDBEventStoreWithEventAsDocument::extractEvents
      ),
      settings
    );

    subscription.start();

    return subscription;
  }

  private static List<? extends Bson> filterSubscription(String streamType) {
    return streamType != null ?
      List.of(
        Aggregates.match(
          Filters.and(
            Filters.eq("operationType", "insert"),
            Filters.regex(
              "fullDocument.metadata.streamName",
              Pattern.compile("^" + Pattern.quote(streamType))
            )
          )
        )) :
      List.of(
        Aggregates.match(
          Filters.eq("operationType", "insert")
        )
      );
  }

  private static List<EventEnvelope> extractEvents(ChangeStreamDocument<EventEnvelope> change) {
    var eventEnvelope = change.getFullDocument();

    return (eventEnvelope != null) ?
      List.of(change.getFullDocument())
      : List.of();
  }

  private MongoCollection<EventStream> streamsCollection() {
    return database.getCollection("streams", EventStream.class);
  }

  private MongoCollection<EventEnvelope> eventsCollection() {
    return database.getCollection("events", EventEnvelope.class);
  }

  private final static UpdateOptions upsert = new UpdateOptions().upsert(true);
}
