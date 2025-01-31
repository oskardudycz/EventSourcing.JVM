package io.eventdriven.eventstores.mongodb.subscriptions;

import java.util.Iterator;

public interface WatchCursor<T> extends Iterator<T>, AutoCloseable {}
