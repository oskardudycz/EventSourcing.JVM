package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution2_immutableentities;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.ICommandBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.IEventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution2_immutableentities.groupcheckouts.GroupCheckoutFacade;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution2_immutableentities.gueststayaccounts.GuestStayAccountFacade;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution2_immutableentities.groupcheckouts.GroupCheckoutsConfig.configureGroupCheckouts;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution2_immutableentities.gueststayaccounts.GuestStayAccountsConfig.configureGuestStayAccounts;

@Configuration
public class RabbitMQSolution2Config {

    private final IEventBus eventBus;
    private final ICommandBus commandBus;
    private final GroupCheckoutFacade groupCheckoutFacade;
    private final GuestStayAccountFacade guestStayAccountFacade;

    public RabbitMQSolution2Config(
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
    public void configureBusinessProcesses() {
        configureGroupCheckouts(eventBus, commandBus, groupCheckoutFacade);
        configureGuestStayAccounts(commandBus, guestStayAccountFacade);
    }
}
