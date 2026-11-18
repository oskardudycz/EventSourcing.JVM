package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts.GroupCheckoutsConfig.configureGroupCheckouts;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.gueststayaccounts.GuestStayAccountsConfig.configureGuestStayAccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts.GroupCheckoutFacade;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class RabbitMQSolution1Config {

    private final MessageBus messageBus;
    private final GroupCheckoutFacade groupCheckoutFacade;
    private final GuestStayAccountFacade guestStayAccountFacade;

    public RabbitMQSolution1Config(
            MessageBus messageBus,
            GroupCheckoutFacade groupCheckoutFacade,
            GuestStayAccountFacade guestStayAccountFacade) {
        this.messageBus = messageBus;
        this.groupCheckoutFacade = groupCheckoutFacade;
        this.guestStayAccountFacade = guestStayAccountFacade;
    }

    @PostConstruct
    public void configureBusinessProcesses() {
        configureGroupCheckouts(messageBus, groupCheckoutFacade);
        configureGuestStayAccounts(messageBus, guestStayAccountFacade);
    }
}
