package io.eventdriven.distributedprocesses.core.processes;

public interface ProcessManager<Id> {
  Id id();

  Object[] pollScheduledCommands();
}
