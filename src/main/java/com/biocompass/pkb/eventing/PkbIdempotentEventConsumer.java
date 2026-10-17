package com.biocompass.pkb.eventing;

import com.biocompass.pkb.command.event.PkbDomainEvent;
import com.biocompass.pkb.command.event.PkbDomainEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnBean(PkbEnrichmentWorkflow.class)
public class PkbIdempotentEventConsumer {

    private final PkbProcessedEventDao processedEventDao;
    private final PkbEnrichmentWorkflow enrichmentWorkflow;
    private final PkbEventingProperties eventingProperties;

    /**
     * The idempotency record and downstream workflow run in one transaction. A workflow failure
     * rolls back the record, allowing Kafka redelivery to retry it safely.
     */
    @Transactional
    public void consume(PkbDomainEvent event) {
        if (event.eventType() != PkbDomainEventType.ENRICHMENT_COMPLETED) {
            return;
        }
        if (!processedEventDao.markProcessedIfNew(event.eventId(), eventingProperties.consumerName())) {
            return;
        }
        enrichmentWorkflow.handleCompleted(event);
    }
}
