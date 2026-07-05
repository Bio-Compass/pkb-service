package com.biocompass.pkb.command.event;

@FunctionalInterface
public interface PkbDomainEventPublisher {

    void publish(PkbDomainEvent event);
}
