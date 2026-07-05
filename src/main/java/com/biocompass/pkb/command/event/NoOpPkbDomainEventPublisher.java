package com.biocompass.pkb.command.event;

import org.springframework.stereotype.Component;

@Component
public class NoOpPkbDomainEventPublisher implements PkbDomainEventPublisher {

    @Override
    public void publish(PkbDomainEvent event) {
        // Kafka-backed publication is added by the eventing implementation step.
    }
}
