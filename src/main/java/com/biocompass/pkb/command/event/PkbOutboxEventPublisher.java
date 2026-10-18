package com.biocompass.pkb.command.event;

import com.biocompass.pkb.eventing.PkbOutboxEventDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persists a domain event in the current command transaction. A separate relay sends it to Kafka only
 * after that transaction has committed, which prevents a successful write from losing its event.
 */
@Component
@RequiredArgsConstructor
public class PkbOutboxEventPublisher implements PkbDomainEventPublisher {

    private final PkbOutboxEventDao outboxEventDao;

    @Override
    public void publish(PkbDomainEvent event) {
        outboxEventDao.save(event);
    }
}
