package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.EventStore;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.StreamName;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventDataCodec;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventEnvelope;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventMetadata;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventTypeMapper;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.streams.EventStream;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
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
    database = this.mongoClient.getDatabase(databaseName);
    eventTypeMapper = EventTypeMapper.DEFAULT;
    eventDataCodec = new EventDataCodec(mongoClient.getCodecRegistry(), eventTypeMapper);
  }

  @Override
  public void init() {
    database.createCollection("streams", new CreateCollectionOptions());

    var collection = database.getCollection("streams");
    collection.createIndex(Indexes.ascending("streamName"), new IndexOptions().unique(true));
  }

  @Override
  public void appendEvents(StreamName streamName, Long expectedVersion, Object... events) {
    var streamType = streamName.streamType();
    var streamId = streamName.streamId();
    var streamNameValue = streamName.toString();

    // Resolve collection
    var collection = collectionFor(streamType);

    var now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

    long currentVersion;

    if (expectedVersion != null) {
      currentVersion = expectedVersion;
    } else {
      var stream = collection.find(Filters.eq("streamName", streamNameValue))
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
    var result = collection.updateOne(
      Filters.and(
        Filters.eq("streamName", streamNameValue),
        Filters.eq("metadata.streamPosition", currentVersion)
      ),
      Updates.combine(
        // Append events
        Updates.pushEach("events", envelopes),
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

    if (result.getModifiedCount() == 0L && result.getUpsertedId() == null)
      throw new IllegalStateException("Expected version did not match the stream version!");
  }

  @Override
  public List<Object> getEvents(StreamName streamName, Long atStreamVersion, LocalDateTime atTimestamp) {
    var streamType = streamName.streamType();

    // Resolve collection
    var collection = collectionFor(streamType);

    // Read events from the stream document
    var stream = collection.find(Filters.eq("streamName", streamName.toString()))
      .projection(Projections.include("events"))
      .first();

    return stream != null ?
      stream.events().stream().map(eventEnvelope ->
        eventEnvelope.getEvent(eventDataCodec)
      ).toList()
      : Collections.emptyList();
  }

  private MongoCollection<EventStream> collectionFor(String streamType) {
    return database.getCollection(streamType, EventStream.class);
  }

  private final static UpdateOptions upsert = new UpdateOptions().upsert(true);
}
