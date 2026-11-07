package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

public class ScheduledGroupCheckoutToDoList {
  private final Database.Collection<GroupCheckout> collection;
  private final GroupCheckoutToDoList groupCheckoutToDoList;

  public ScheduledGroupCheckoutToDoList(
    Database.Collection<GroupCheckout> collection,
    GroupCheckoutToDoList groupCheckoutToDoList
  ) {
    this.collection = collection;
    this.groupCheckoutToDoList = groupCheckoutToDoList;
  }

  @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
  public void handleAll() {
    collection.findAll().forEach(groupCheckoutToDoList::handle);
  }
}
