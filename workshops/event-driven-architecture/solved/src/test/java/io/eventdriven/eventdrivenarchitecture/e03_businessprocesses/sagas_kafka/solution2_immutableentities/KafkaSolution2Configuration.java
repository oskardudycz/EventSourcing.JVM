package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.ICommandBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.IEventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.groupcheckouts.GroupCheckoutFacade;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.groupcheckouts.GroupCheckoutsConfig;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.gueststayaccounts.GuestStayAccountFacade;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.gueststayaccounts.GuestStayAccountsConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class KafkaSolution2Configuration {

    private final IEventBus eventBus;
    private final ICommandBus commandBus;
    private final GroupCheckoutFacade groupCheckoutFacade;
    private final GuestStayAccountFacade guestStayAccountFacade;

    public KafkaSolution2Configuration(
        IEventBus eventBus,
        ICommandBus commandBus,
        GroupCheckoutFacade groupCheckoutFacade,
        GuestStayAccountFacade guestStayAccountFacade
    ) {
        this.eventBus = eventBus;
        this.commandBus = commandBus;
        this.groupCheckoutFacade = groupCheckoutFacade;
        this.guestStayAccountFacade = guestStayAccountFacade;
    }

    @PostConstruct
    public void configure() {
        GroupCheckoutsConfig.configureGroupCheckouts(eventBus, commandBus, groupCheckoutFacade);
        GuestStayAccountsConfig.configureGuestStayAccounts(commandBus, guestStayAccountFacade);
    }
}