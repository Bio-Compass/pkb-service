package com.biocompass.pkb.command.handler;

import com.biocompass.pkb.command.event.PkbDomainEvent;
import com.biocompass.pkb.command.event.PkbDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PkbAfterCommitEventPublisher {

    private final PkbDomainEventPublisher eventPublisher;

    void publishAfterCommit(PkbDomainEvent event) {
        // The production publisher records the event in the database outbox inside this transaction.
        // Kafka delivery is deliberately deferred to the relay after the transaction commits.
        eventPublisher.publish(event);
    }
}
