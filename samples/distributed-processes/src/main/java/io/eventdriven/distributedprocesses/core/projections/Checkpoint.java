package io.eventdriven.distributedprocesses.core.projections;

public record Checkpoint(long transactionId, long position) {}
