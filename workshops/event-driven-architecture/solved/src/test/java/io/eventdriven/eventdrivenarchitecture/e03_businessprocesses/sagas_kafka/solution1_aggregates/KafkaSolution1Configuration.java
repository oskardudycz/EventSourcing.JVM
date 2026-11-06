package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution1_aggregates;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution1_aggregates.groupcheckouts.GroupCheckoutFacade;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution1_aggregates.groupcheckouts.GroupCheckoutsConfig;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution1_aggregates.gueststayaccounts.GuestStayAccountsConfig;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class KafkaSolution1Configuration {

    private final MessageBus messageBus;
    private final GroupCheckoutFacade groupCheckoutFacade;
    private final GuestStayAccountFacade guestStayAccountFacade;

    public KafkaSolution1Configuration(
        MessageBus messageBus,
        GroupCheckoutFacade groupCheckoutFacade,
        GuestStayAccountFacade guestStayAccountFacade
    ) {
        this.messageBus = messageBus;
        this.groupCheckoutFacade = groupCheckoutFacade;
        this.guestStayAccountFacade = guestStayAccountFacade;
    }

    @PostConstruct
    public void configure() {
        GroupCheckoutsConfig.configureGroupCheckouts(messageBus, groupCheckoutFacade);
        GuestStayAccountsConfig.configureGuestStayAccounts(messageBus, guestStayAccountFacade);
    }
}
