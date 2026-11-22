package io.eventdriven.core.eventstore.kurrentdb;

import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.ResolvedEvent;
import io.kurrent.dbclient.SubscribeToAllOptions;
import io.kurrent.dbclient.Subscription;
import io.kurrent.dbclient.SubscriptionListener;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

public final class KurrentDBSubscriptionToAll implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(KurrentDBSubscriptionToAll.class);

    private final KurrentDBClient client;
    private final String name;
    private final Supplier<SubscribeToAllOptions> options;
    private final Consumer<ResolvedEvent> handle;
    private final BackOff backOff;
    private final ScheduledExecutorService scheduler;
    private BackOffExecution backOffExecution;
    private volatile Subscription current;
    private final ReentrantLock handling = new ReentrantLock();
    private volatile boolean closed = false;

    public KurrentDBSubscriptionToAll(
            KurrentDBClient client,
            String name,
            Supplier<SubscribeToAllOptions> options,
            Consumer<ResolvedEvent> handle,
            BackOff backOff,
            ScheduledExecutorService scheduler) {
        this.client = client;
        this.name = name;
        this.options = options;
        this.handle = handle;
        this.backOff = backOff;
        this.scheduler = scheduler;
        this.backOffExecution = backOff.start();
    }

    public void start() {
        if (closed) return;

        var failed = new AtomicBoolean(false);

        try {
            current = client.subscribeToAll(listener(failed), options.get()).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            fail(failed, "Subscription '%s' could not start".formatted(name), e);
        }
    }

    @Override
    public void close() {
        closed = true;

        var subscription = current;
        if (subscription != null) subscription.stop();

        waitForRunningHandler();
    }

    private void waitForRunningHandler() {
        handling.lock();
        handling.unlock();
    }

    private SubscriptionListener listener(AtomicBoolean failed) {
        return new SubscriptionListener() {
            @Override
            public void onEvent(Subscription subscription, ResolvedEvent resolvedEvent) {
                if (failed.get() || closed) return;

                handling.lock();
                try {
                    if (closed) return;

                    handle.accept(resolvedEvent);
                    resetBackOff();
                } catch (RuntimeException e) {
                    if (fail(failed, "Subscription '%s' failed on an event".formatted(name), e)) subscription.stop();
                } finally {
                    handling.unlock();
                }
            }

            @Override
            public void onCancelled(Subscription subscription, Throwable throwable) {
                if (throwable != null) fail(failed, "Subscription '%s' was cancelled".formatted(name), throwable);
            }
        };
    }

    private boolean fail(AtomicBoolean failed, String message, Throwable cause) {
        if (closed || !failed.compareAndSet(false, true)) return false;

        var delay = nextDelay();
        logger.error("{}; it resubscribes from its checkpoint in {} ms", message, delay, cause);
        scheduler.schedule(this::start, delay, TimeUnit.MILLISECONDS);

        return true;
    }

    private synchronized long nextDelay() {
        var delay = backOffExecution.nextBackOff();

        if (delay != BackOffExecution.STOP) return delay;

        backOffExecution = backOff.start();

        return backOffExecution.nextBackOff();
    }

    private synchronized void resetBackOff() {
        backOffExecution = backOff.start();
    }
}
