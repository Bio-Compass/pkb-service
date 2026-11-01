package com.biocompass.pkb.kafka;

import com.biocompass.pkb.command.event.PkbDomainEventType;
import com.biocompass.pkb.eventing.PkbEventingProperties;
import java.util.HashSet;
import java.util.Map;
import java.util.function.BiFunction;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;

/** Routes by transport topic, including when deserialization has failed. */
final class PkbDeadLetterDestinationResolver
        implements BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> {

    private final Map<String, String> destinations;

    PkbDeadLetterDestinationResolver(
            String commandTopic, PkbKafkaProperties commands, PkbEventingProperties events) {
        var topicNames = new HashSet<String>();
        for (var eventType : PkbDomainEventType.values()) {
            topicNames.add(eventType.value());
        }
        for (var topic : new String[] {commandTopic, commands.deadLetterTopic(), commands.replayTopic(),
                events.deadLetterTopic(), events.replayTopic()}) {
            if (topic == null || topic.isBlank() || !topicNames.add(topic)) {
                throw new IllegalArgumentException("PKB command, event, DLT and replay topics must be distinct and non-blank");
            }
        }
        destinations = Map.of(
                commandTopic, commands.deadLetterTopic(),
                commands.replayTopic(), commands.deadLetterTopic(),
                PkbDomainEventType.ENRICHMENT_COMPLETED.value(), events.deadLetterTopic(),
                events.replayTopic(), events.deadLetterTopic());
    }

    @Override
    public TopicPartition apply(ConsumerRecord<?, ?> record, Exception exception) {
        var destination = destinations.get(record.topic());
        if (destination == null) {
            throw new IllegalArgumentException("No PKB dead-letter route for topic: " + record.topic());
        }
        return new TopicPartition(destination, record.partition());
    }
}
