package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions;

import java.util.Iterator;

public interface WatchCursor<T> extends Iterator<T>, AutoCloseable {}
