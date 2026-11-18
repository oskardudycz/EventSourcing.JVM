package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;

public class ScheduledGroupCheckoutToDoList {
    private final Database.Collection<GroupCheckout> collection;
    private final GroupCheckoutToDoList groupCheckoutToDoList;

    public ScheduledGroupCheckoutToDoList(
            Database.Collection<GroupCheckout> collection, GroupCheckoutToDoList groupCheckoutToDoList) {
        this.collection = collection;
        this.groupCheckoutToDoList = groupCheckoutToDoList;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void handleAll() {
        collection.findAll().forEach(groupCheckoutToDoList::tryComplete);
    }
}
