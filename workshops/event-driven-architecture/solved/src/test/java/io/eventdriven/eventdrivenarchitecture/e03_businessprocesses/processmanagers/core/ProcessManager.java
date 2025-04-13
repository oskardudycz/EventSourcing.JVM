package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.core;

public interface ProcessManager<Id> {
  Id id();

  Message[] dequeueUncommittedMessages();
}
