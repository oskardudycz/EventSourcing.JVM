package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.stream_as_document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.EventStore;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.StreamName;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.StreamType;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventDataCodec;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventEnvelope;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventMetadata;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventTypeMapper;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.stream_as_document.streams.EventStream;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions.BatchingPolicy;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions.EventSubscription;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions.MongoEventSubscriptionService;
import org.bson.BsonDocumentReader;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.conversions.Bson;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class MongoDBEventStore implements EventStore {
  private final MongoClient mongoClient;
  private final MongoDatabase database;
  private final EventDataCodec eventDataCodec;
  private final Codec<EventEnvelope> eventEnvelopeCodec;
  private final EventTypeMapper eventTypeMapper;


  public MongoDBEventStore(MongoClient mongoClient, String databaseName) {
    this.mongoClient = mongoClient;
    database = this.mongoClient.getDatabase(databaseName);
    eventTypeMapper = EventTypeMapper.DEFAULT;

    var codecRegistry = mongoClient.getCodecRegistry();
    eventDataCodec = new EventDataCodec(codecRegistry, eventTypeMapper);
    eventEnvelopeCodec = codecRegistry.get(EventEnvelope.class);
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


  public <Type> EventSubscription subscribe(
    Class<Type> streamType,
    Consumer<List<EventEnvelope>> handler
  ) {
    return new MongoEventSubscriptionService<>(
      collectionFor(StreamType.of(streamType)),
      MongoDBEventStore::filterSubscription,
      this::extractEvents
    ).subscribe(streamType, handler, BatchingPolicy.DEFAULT);
  }

  public <Type> EventSubscription subscribe(
    Class<Type> streamType,
    Consumer<List<EventEnvelope>> handler,
    BatchingPolicy batchingPolicy
  ) {
    return new MongoEventSubscriptionService<>(
      collectionFor(StreamType.of(streamType)),
      MongoDBEventStore::filterSubscription,
      this::extractEvents
    ).subscribe(streamType, handler, batchingPolicy);
  }

  private static List<? extends Bson> filterSubscription(String streamType) {
    return List.of(
      Aggregates.match(
        Filters.or(
          Filters.eq("operationType", "insert"),
          Filters.eq("operationType", "update")
          // TODO: filtering only for events
        )
      )
    );
  }

  private List<EventEnvelope> extractEvents(ChangeStreamDocument<EventStream> change) {
    var operationType = change.getOperationType();

    if (operationType == null)
      return List.of();

    switch (operationType.getValue()) {
      case "insert" -> {
        var fullDocument = change.getFullDocument();
        if (fullDocument == null)
          return List.of();

        return fullDocument.events();
      }
      case "update" -> {
        var updateDescription = change.getUpdateDescription();

        if (updateDescription == null)
          return List.of();

        var updatedFields = updateDescription.getUpdatedFields();

        if (updatedFields == null)
          return List.of();

        return updatedFields.entrySet().stream()
          .filter(e -> e.getKey().startsWith("events."))
          .map(Map.Entry::getValue)
          .map(bsonEvent ->
            eventEnvelopeCodec.decode(
              new BsonDocumentReader(bsonEvent.asDocument()),
              DecoderContext.builder().build()
            )
          )
          .toList();
      }
      default -> {
        return List.of();
      }
    }
  }

  private MongoCollection<EventStream> collectionFor(String streamType) {
    return database.getCollection(streamType, EventStream.class);
  }

  private final static UpdateOptions upsert = new UpdateOptions().upsert(true);
}
