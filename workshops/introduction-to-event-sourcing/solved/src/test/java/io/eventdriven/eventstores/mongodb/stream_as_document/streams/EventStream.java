package io.eventdriven.eventstores.mongodb.stream_as_document.streams;

import io.eventdriven.eventstores.mongodb.events.EventEnvelope;
import java.util.List;
import org.bson.types.ObjectId;

public record EventStream(ObjectId id, String streamName, List<EventEnvelope> events, StreamMetadata metadata) {}
