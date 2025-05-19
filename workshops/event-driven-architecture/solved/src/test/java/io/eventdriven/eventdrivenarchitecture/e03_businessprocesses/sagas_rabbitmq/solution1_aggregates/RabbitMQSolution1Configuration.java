package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.ICommandBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.IEventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts.GroupCheckoutFacade;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts.GroupCheckoutsConfig;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.gueststayaccounts.GuestStayAccountsConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class RabbitMQSolution1Configuration {

    @Autowired
    private IEventBus eventBus;

    @Autowired
    private ICommandBus commandBus;

    @Autowired
    private GroupCheckoutFacade groupCheckoutFacade;

    @Autowired
    private GuestStayAccountFacade guestStayAccountFacade;

    @PostConstruct
    public void configure() {
        GroupCheckoutsConfig.configureGroupCheckouts(eventBus, commandBus, groupCheckoutFacade);
        GuestStayAccountsConfig.configureGuestStayAccounts(commandBus, guestStayAccountFacade);
    }
}
