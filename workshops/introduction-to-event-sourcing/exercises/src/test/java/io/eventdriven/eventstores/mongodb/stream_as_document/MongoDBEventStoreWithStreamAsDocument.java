package io.eventdriven.eventstores.mongodb.stream_as_document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import io.eventdriven.eventstores.StreamName;
import io.eventdriven.eventstores.mongodb.MongoDBEventStore;
import io.eventdriven.eventstores.mongodb.events.EventDataCodec;
import io.eventdriven.eventstores.mongodb.events.EventEnvelope;
import io.eventdriven.eventstores.mongodb.events.EventMetadata;
import io.eventdriven.eventstores.mongodb.events.EventTypeMapper;
import io.eventdriven.eventstores.mongodb.stream_as_document.streams.EventStream;
import io.eventdriven.eventstores.mongodb.subscriptions.EventSubscription;
import io.eventdriven.eventstores.mongodb.subscriptions.EventSubscriptionSettings;
import io.eventdriven.eventstores.mongodb.subscriptions.MongoEventStreamCursor;
import org.bson.BsonDocumentReader;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.conversions.Bson;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

public class MongoDBEventStoreWithStreamAsDocument implements MongoDBEventStore {
  private final MongoClient mongoClient;
  private final MongoDatabase database;
  private final EventDataCodec eventDataCodec;
  private final Codec<EventEnvelope> eventEnvelopeCodec;
  private final EventTypeMapper eventTypeMapper;


  public MongoDBEventStoreWithStreamAsDocument(MongoClient mongoClient, String databaseName) {
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
  public AppendResult appendToStream(StreamName streamName, Long expectedStreamPosition, Object... events) {
    var streamType = streamName.streamType();
    var streamId = streamName.streamId();
    var streamNameValue = streamName.toString();

    // Resolve collection
    var collection = collectionFor(streamType);

    var now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

    long currentStreamPosition;

    if (expectedStreamPosition != null) {
      currentStreamPosition = expectedStreamPosition;
    } else {
      var stream = collection.find(Filters.eq("streamName", streamNameValue))
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
    var result = collection.updateOne(
      Filters.and(
        Filters.eq("streamName", streamNameValue),
        Filters.eq("metadata.streamPosition", currentStreamPosition)
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

    return new AppendResult(currentStreamPosition + events.length);
  }

  @Override
  public ReadStreamResult readStream(StreamName streamName) {
    var streamType = streamName.streamType();

    // Resolve collection
    var collection = collectionFor(streamType);

    // Read events from the stream document
    var stream = collection.find(Filters.eq("streamName", streamName.toString()))
      .projection(Projections.include("events", "metadata.streamPosition"))
      .first();

    if (stream == null) {
      return new ReadStreamResult(0, List.of());
    }

    var events = stream.events().stream()
      .map(eventEnvelope -> eventEnvelope.getEvent(eventDataCodec))
      .toList();

    return new ReadStreamResult(stream.metadata().streamPosition(), events);
  }

  public EventSubscription subscribe(EventSubscriptionSettings settings) {
    var subscription = new EventSubscription(
      () -> MongoEventStreamCursor.from(
        collectionFor(settings.streamType()),
        filterSubscription(),
        this::extractEvents
      ),
      settings
    );

    subscription.start();

    return subscription;
  }

  private static List<? extends Bson> filterSubscription() {
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
