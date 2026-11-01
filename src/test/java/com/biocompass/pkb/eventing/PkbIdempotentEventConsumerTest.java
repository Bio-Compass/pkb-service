package com.biocompass.pkb.eventing;

import com.biocompass.pkb.command.event.PkbDomainEvent;
import com.biocompass.pkb.command.event.PkbDomainEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PkbIdempotentEventConsumerTest {

    @Mock
    private PkbProcessedEventDao processedEventDao;

    @Mock
    private PkbEnrichmentWorkflow enrichmentWorkflow;

    @Test
    void invokesTheEnrichmentWorkflowOnlyForTheFirstDelivery() {
        var event = PkbDomainEvent.enrichmentCompleted(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "workflow-1"
        );
        when(processedEventDao.markProcessedIfNew(event.eventId(), "pkb-enrichment")).thenReturn(true, false);

        var consumer = consumer();
        consumer.consume(event);
        consumer.consume(event);

        verify(processedEventDao, times(2)).markProcessedIfNew(event.eventId(), "pkb-enrichment");
        verify(enrichmentWorkflow).handleCompleted(event);
    }

    @Test
    void ignoresEventsThatAreNotEnrichmentCompletionEvents() {
        var event = new PkbDomainEvent(
                UUID.randomUUID(),
                PkbDomainEventType.ENRICHMENT_REQUESTED,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                "workflow-1"
        );

        consumer().consume(event);

        verify(processedEventDao, never()).markProcessedIfNew(event.eventId(), "pkb-enrichment");
        verify(enrichmentWorkflow, never()).handleCompleted(event);
    }

    private PkbIdempotentEventConsumer consumer() {
        return new PkbIdempotentEventConsumer(
                processedEventDao,
                enrichmentWorkflow,
                new PkbEventingProperties(false, null, 50, "pkb-enrichment", 3, Duration.ofSeconds(5), null, null)
        );
    }
}
