package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.micrometer.observation.annotation.Observed;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
@Observed(name = "group.checkout")
public class GroupCheckoutFacade {
    private final Database.Collection<GroupCheckout> collection;
    private final MessageBus messageBus;

    public GroupCheckoutFacade(Database database, MessageBus messageBus) {
        this.collection = database.collection(GroupCheckout.class);
        this.messageBus = messageBus;
    }

    public void initiateGroupCheckout(InitiateGroupCheckout command) {
        var groupCheckout = GroupCheckout.initiate(
                command.groupCheckoutId(), command.clerkId(), command.guestStayAccountIds(), command.now());

        collection.store(command.groupCheckoutId(), groupCheckout);
        messageBus.publish(groupCheckout.dequeueUncommittedEvents());
    }

    public void recordGuestCheckoutCompletion(RecordGuestCheckoutCompletion command) {
        var groupCheckout = collection.get(command.groupCheckoutId()).orElse(null);
        if (groupCheckout == null) return;

        groupCheckout.recordGuestCheckoutCompletion(command.guestStayAccountId(), command.now());

        collection.store(command.groupCheckoutId(), groupCheckout);
        messageBus.publish(groupCheckout.dequeueUncommittedEvents());
    }

    public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command) {
        var groupCheckout = collection.get(command.groupCheckoutId()).orElse(null);
        if (groupCheckout == null) return;

        groupCheckout.recordGuestCheckoutFailure(command.guestStayAccountId(), command.now());

        collection.store(command.groupCheckoutId(), groupCheckout);
        messageBus.publish(groupCheckout.dequeueUncommittedEvents());
    }

    public sealed interface GroupCheckoutCommand {
        record InitiateGroupCheckout(UUID groupCheckoutId, UUID clerkId, UUID[] guestStayAccountIds, OffsetDateTime now)
                implements GroupCheckoutCommand {}

        record RecordGuestCheckoutCompletion(UUID groupCheckoutId, UUID guestStayAccountId, OffsetDateTime now)
                implements GroupCheckoutCommand {}

        record RecordGuestCheckoutFailure(UUID groupCheckoutId, UUID guestStayAccountId, OffsetDateTime now)
                implements GroupCheckoutCommand {}
    }
}
