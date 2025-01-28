package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.stream_as_document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.EventStore;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.StreamName;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.StreamType;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.stream_as_document.events.EventDataCodec;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.stream_as_document.events.EventEnvelope;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.stream_as_document.events.EventMetadata;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.stream_as_document.events.EventTypeMapper;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.stream_as_document.streams.EventStream;
import org.bson.BsonDocumentReader;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
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
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();


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

  public <Type> void subscribe(Class<Type> streamType, Consumer<EventEnvelope[]> handler) {
    executorService.execute(() -> {
      var collection = collectionFor(StreamType.of(streamType));

      var pipeline = Collections.singletonList(
        Aggregates.project(Projections.fields(
          Projections.include("events"),
          Projections.excludeId() // Optionally exclude the root `_id`
        ))
      );

      try (var cursor = collection.watch().cursor()) {
        while (cursor.hasNext()) {
          var change = cursor.next();

          var operationType = change.getOperationType();

          if (operationType == null)
            continue;

          switch (operationType.getValue()) {
            case "insert" -> {
              var fullDocument = change.getFullDocument();
              if (fullDocument != null) {
                var events = fullDocument
                  .events()
                  .toArray(EventEnvelope[]::new);
                handler.accept(events);
              }
            }
            case "update" -> {
              var updateDescription = change.getUpdateDescription();

              if (updateDescription == null)
                continue;

              var updatedFields = updateDescription.getUpdatedFields();

              if (updatedFields == null)
                continue;

              var events = updatedFields.entrySet().stream()
                .filter(e -> e.getKey().startsWith("events."))
                .map(kv -> kv.getValue())
                .map(bsonEvent ->
                  eventEnvelopeCodec.decode(
                    new BsonDocumentReader(bsonEvent.asDocument()),
                    DecoderContext.builder().build()
                  )
                )
                .toArray(EventEnvelope[]::new);

              if (events.length > 0) {
                handler.accept(events);
              }
            }
          }
        }
      } catch (Exception ex) {
        System.out.println(ex.getMessage());
      }
    });
  }

  private MongoCollection<EventStream> collectionFor(String streamType) {
    return database.getCollection(streamType, EventStream.class);
  }

  private final static UpdateOptions upsert = new UpdateOptions().upsert(true);
}
