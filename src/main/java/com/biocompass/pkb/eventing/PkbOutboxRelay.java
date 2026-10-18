package com.biocompass.pkb.eventing;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "biocompass.pkb.eventing", name = "enabled", havingValue = "true")
public class PkbOutboxRelay {

    private final PkbOutboxEventDao outboxEventDao;
    private final KafkaPkbEventPublisher kafkaEventPublisher;
    private final PkbEventingProperties eventingProperties;

    /**
     * Kafka delivery is at-least-once: a crash after Kafka acknowledges and before this transaction
     * commits may retry an event. Consumers therefore persist an idempotency key before handling it.
     */
    @Scheduled(fixedDelayString = "${biocompass.pkb.eventing.relay-interval:PT1S}")
    @Transactional
    public void relayUnpublishedEvents() {
        for (var outboxEvent : outboxEventDao.claimUnpublished(eventingProperties.relayBatchSize())) {
            try {
                kafkaEventPublisher.publish(outboxEvent.toDomainEvent());
                outboxEvent.markPublished();
            } catch (RuntimeException exception) {
                outboxEvent.markFailed(exception.getMessage());
            }
        }
    }
}
