package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.core;

import java.util.LinkedList;
import java.util.Queue;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.core.Message.*;

public abstract class AbstractProcessManager<Event, Id> implements ProcessManager<Id> {
  protected Id id;
  protected int version = -1;

  private final Queue<Message> uncommittedMessages = new LinkedList<>();

  public Id id() {
    return id;
  }

  public Message[] dequeueUncommittedMessages() {
    var dequeuedMessages = uncommittedMessages.toArray(Message[]::new);

    uncommittedMessages.clear();

    return dequeuedMessages;
  }

  public abstract void apply(Event event);

  protected void enqueue(Event event) {
    uncommittedMessages.add(Publish(event));
    apply(event);
    version++;
  }

  protected void schedule(Object command) {
    uncommittedMessages.add(Send(command));
  }
}
