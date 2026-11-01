package com.biocompass.pkb.eventing;

import com.biocompass.pkb.command.event.PkbDomainEvent;
import com.biocompass.pkb.command.event.PkbDomainEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;

@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "biocompass.pkb.eventing", name = "enabled", havingValue = "true")
@ConditionalOnBean(PkbEnrichmentWorkflow.class)
public class PkbKafkaEventConsumer {

    private final PkbIdempotentEventConsumer eventConsumer;
    private final PkbEventingProperties eventingProperties;

    public String[] topics() {
        return new String[] {PkbDomainEventType.ENRICHMENT_COMPLETED.value(), eventingProperties.replayTopic()};
    }

    @KafkaListener(
            topics = "#{__listener.topics()}",
            groupId = "${biocompass.pkb.eventing.consumer-name:pkb-enrichment}",
            properties = "spring.json.value.default.type=com.biocompass.pkb.command.event.PkbDomainEvent"
    )
    public void consumeEnrichmentCompleted(PkbDomainEvent event) {
        eventConsumer.consume(event);
    }
}
