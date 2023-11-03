package io.eventdriven.distributedprocesses.core.processes;

import java.util.LinkedList;
import java.util.Queue;

public abstract class AbstractProcessManager<Id> implements ProcessManager<Id> {
  protected Id id;
  protected long version = -1;

  private final Queue<Object> scheduledCommands = new LinkedList<>();

  public Id id() {
    return id;
  }

  public void incrementVersion() {
    version++;
  }

  public Object[] pollScheduledCommands() {
    var scheduledCommands = this.scheduledCommands.toArray();

    this.scheduledCommands.clear();

    return scheduledCommands;
  }

  protected void schedule(Object command) {
    scheduledCommands.add(command);
  }
}
