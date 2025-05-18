package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.core;

public interface ProcessManager<Id> {
  Id id();

  Message[] dequeueUncommittedMessages();
}
