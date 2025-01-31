package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions;

import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventEnvelope;

import java.util.List;
import java.util.function.Function;

public class MongoEventStreamCursor<TDocument> implements WatchCursor<List<EventEnvelope>> {
  private final MongoChangeStreamCursor<ChangeStreamDocument<TDocument>> changeStreamCursor;
  private final Function<ChangeStreamDocument<TDocument>, List<EventEnvelope>>  extractEvents;

  public MongoEventStreamCursor(
    ChangeStreamIterable<TDocument> changeStream,
    Function<ChangeStreamDocument<TDocument>, List<EventEnvelope>> extractEvents
  ) {
    this.changeStreamCursor = changeStream.cursor();
    this.extractEvents = extractEvents;
  }

  @Override
  public boolean hasNext() {
    return changeStreamCursor.hasNext();
  }

  @Override
  public List<EventEnvelope> next() {
    return extractEvents.apply(changeStreamCursor.next());
  }

  @Override
  public void close() {
    changeStreamCursor.close();
  }
}
