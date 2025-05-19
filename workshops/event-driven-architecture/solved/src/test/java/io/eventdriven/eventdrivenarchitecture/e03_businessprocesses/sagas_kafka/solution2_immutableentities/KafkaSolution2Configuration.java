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