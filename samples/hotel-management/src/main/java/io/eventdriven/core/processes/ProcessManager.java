package io.eventdriven.core.processes;

public interface ProcessManager<Id> {
    Id id();

    Object[] pollScheduledCommands();
}
