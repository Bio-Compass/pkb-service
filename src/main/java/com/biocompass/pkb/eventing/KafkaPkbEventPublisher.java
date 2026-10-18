package com.biocompass.pkb.eventing;

import com.biocompass.pkb.command.event.PkbDomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "biocompass.pkb.eventing", name = "enabled", havingValue = "true")
public class KafkaPkbEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PkbEventingProperties eventingProperties;

    public void publish(PkbDomainEvent event) {
        try {
            kafkaTemplate.send(event.eventType().value(), event.aggregateKey(), event)
                    .get(eventingProperties.publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PkbKafkaPublicationException("Interrupted while publishing PKB event " + event.eventId(), exception);
        } catch (ExecutionException exception) {
            throw new PkbKafkaPublicationException("Unable to publish PKB event " + event.eventId(), exception.getCause());
        } catch (TimeoutException exception) {
            throw new PkbKafkaPublicationException("Timed out publishing PKB event " + event.eventId(), exception);
        }
    }
}
