package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.event_as_document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.EventStore;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.StreamName;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.event_as_document.events.EventDataCodec;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.event_as_document.events.EventEnvelope;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.event_as_document.events.EventMetadata;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.event_as_document.events.EventTypeMapper;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.event_as_document.streams.EventStream;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public class MongoDBEventStore implements EventStore {
  private final MongoClient mongoClient;
  private final MongoDatabase database;
  private final EventDataCodec eventDataCodec;
  private final EventTypeMapper eventTypeMapper;

  public MongoDBEventStore(MongoClient mongoClient, String databaseName) {
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
  public void appendEvents(StreamName streamName, Long expectedVersion, Object... events) {
    var streamType = streamName.streamType();
    var streamId = streamName.streamId();
    var streamNameValue = streamName.toString();

    // Resolve collections
    var streamsCollection = streamsCollection();
    var eventsCollection = eventsCollection();

    var now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

    mongoClient.startSession().withTransaction(() -> {
      long currentVersion;
      if (expectedVersion != null) {
        currentVersion = expectedVersion;
      } else {
        var stream = streamsCollection.find(Filters.eq("streamName", streamNameValue))
          .projection(Projections.include("metadata.streamPosition"))
          .first();

        currentVersion = stream != null ?
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
              currentVersion + index + 1,
              streamNameValue
            ),
            eventDataCodec
          );
        }).toList();

      // Append events upserting the document
      var streamUpdateResult = streamsCollection.updateOne(
        Filters.and(
          Filters.eq("streamName", streamNameValue),
          Filters.eq("metadata.streamPosition", currentVersion)
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

      return true;
    });
  }

  @Override
  public List<Object> getEvents(StreamName streamName, Long atStreamVersion, LocalDateTime atTimestamp) {
    var eventsCollection = eventsCollection();

    return eventsCollection
      .find(Filters.eq("metadata.streamName", streamName.toString()))
      .into(new ArrayList<>())
      .stream()
      .map(eventEnvelope -> eventEnvelope.getEvent(eventDataCodec))
      .toList();
  }

  private MongoCollection<EventStream> streamsCollection() {
    return database.getCollection("streams", EventStream.class);
  }

  private MongoCollection<EventEnvelope> eventsCollection() {
    return database.getCollection("events", EventEnvelope.class);
  }

  private final static UpdateOptions upsert = new UpdateOptions().upsert(true);
}
