package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventEnvelope;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;

public class MongoEventSubscriptionService<TDocument> {
    protected final MongoCollection<TDocument> streamsCollection;
    private final Function<String, List<? extends Bson>> filterSubscription;
    private final Function<ChangeStreamDocument<TDocument>, List<EventEnvelope>> extractEvents;
    protected final ExecutorService executorService;

    public MongoEventSubscriptionService(
            MongoCollection<TDocument> streamsCollection,
            Function<String, List<? extends Bson>> filterSubscription,
            Function<ChangeStreamDocument<TDocument>, List<EventEnvelope>> extractEvents
    ) {
        this(
                streamsCollection,
                filterSubscription,
                extractEvents,
                Executors.newSingleThreadExecutor()
        );
    }

    public MongoEventSubscriptionService(
            MongoCollection<TDocument> streamsCollection,
            Function<String, List<? extends Bson>> filterSubscription,
            Function<ChangeStreamDocument<TDocument>, List<EventEnvelope>> extractEvents,
            ExecutorService executorService
    ) {
        this.streamsCollection = streamsCollection;
        this.filterSubscription = filterSubscription;
        this.extractEvents = extractEvents;
        this.executorService = executorService;
    }

    public EventSubscription subscribe(EventSubscriptionSettings settings) {

        var subscription = new EventSubscription(settings, this::listenToChanges);

        subscription.start();

        return subscription;
    }

    private void listenToChanges(EventSubscriptionSettings settings, Consumer<EventEnvelope> put) {
        var watch = streamsCollection.watch(filterSubscription.apply(settings.streamType()));

        try (var cursor = new MongoEventStreamCursor<>(watch, extractEvents)) {
            while (cursor.hasNext()) {
                cursor.next().forEach(put);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
