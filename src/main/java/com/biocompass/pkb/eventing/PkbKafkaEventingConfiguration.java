package com.biocompass.pkb.eventing;

import com.biocompass.pkb.command.event.PkbDomainEventType;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@Import({PkbIdempotentEventConsumer.class, PkbKafkaEventConsumer.class})
@EnableScheduling
@ConditionalOnProperty(prefix = "biocompass.pkb.eventing", name = "enabled", havingValue = "true")
public class PkbKafkaEventingConfiguration {

    @Bean
    KafkaAdmin.NewTopics pkbEventTopics(PkbEventingProperties eventingProperties) {
        var topics = java.util.Arrays.stream(PkbDomainEventType.values())
                .map(eventType -> TopicBuilder.name(eventType.value())
                        .partitions(eventingProperties.topicPartitions())
                        .replicas(1)
                        .build())
                .toArray(NewTopic[]::new);
        return new KafkaAdmin.NewTopics(topics);
    }
}
