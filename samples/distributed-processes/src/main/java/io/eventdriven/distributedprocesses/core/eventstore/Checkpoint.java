package io.eventdriven.distributedprocesses.core.eventstore;

public record Checkpoint(long transactionId, long position) {}
