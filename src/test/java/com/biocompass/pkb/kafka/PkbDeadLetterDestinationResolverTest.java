package com.biocompass.pkb.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biocompass.pkb.command.event.PkbDomainEventType;
import com.biocompass.pkb.eventing.PkbEventingProperties;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class PkbDeadLetterDestinationResolverTest {
    @Test
    void routesDefaultsAndOverridesBySourceTopicAndPreservesPartition() {
        for (var overrides : new boolean[] {false, true}) {
            var commands = commands(overrides ? "custom.command.dlt" : null, overrides ? "custom.command.replay" : null);
            var events = events(overrides ? "custom.event.dlt" : null, overrides ? "custom.event.replay" : null);
            var resolver = new PkbDeadLetterDestinationResolver("command.ingress", commands, events);
            assertRoute(resolver, "command.ingress", commands.deadLetterTopic());
            assertRoute(resolver, commands.replayTopic(), commands.deadLetterTopic());
            assertRoute(resolver, "pkb.enrichment.completed", events.deadLetterTopic());
            assertRoute(resolver, events.replayTopic(), events.deadLetterTopic());
        }
    }

    @Test
    void defaultsAndWhitespaceAreNormalized() {
        assertThat(events(null, " ").deadLetterTopic()).isEqualTo("pkb.events.dlt.v1");
        assertThat(events(null, " ").replayTopic()).isEqualTo("pkb.events.replay.v1");
        assertThat(events(" custom.dlt ", " custom.replay ").deadLetterTopic()).isEqualTo("custom.dlt");
        assertThat(events(" custom.dlt ", " custom.replay ").replayTopic()).isEqualTo("custom.replay");
    }

    @Test
    void rejectsUnknownSourcesIncludingDltTopics() {
        var resolver = new PkbDeadLetterDestinationResolver("commands", commands(null, null), events(null, null));
        for (var topic : List.of("unknown", "pkb.events.dlt.v1", "pkb.commands.ingress.dlt.v1", "pkb.item.created")) {
            assertThatThrownBy(() -> resolver.apply(record(topic), new RuntimeException()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No PKB dead-letter route");
        }
    }

    @Test
    void rejectsEveryPairOfConfiguredTopicCollisionsAndEveryReservedEventTopic() {
        for (int first = 0; first < 5; first++) {
            for (int second = first + 1; second < 5; second++) {
                var topics = new ArrayList<>(List.of("commands", "command.dlt", "command.replay", "event.dlt", "event.replay"));
                topics.set(second, topics.get(first));
                assertInvalid(topics);
            }
            for (var event : PkbDomainEventType.values()) {
                var topics = new ArrayList<>(List.of("commands", "command.dlt", "command.replay", "event.dlt", "event.replay"));
                topics.set(first, event.value());
                assertInvalid(topics);
            }
        }
        assertInvalid(java.util.Arrays.asList(null, "command.dlt", "command.replay", "event.dlt", "event.replay"));
        assertInvalid(List.of(" ", "command.dlt", "command.replay", "event.dlt", "event.replay"));
    }

    private static void assertInvalid(List<String> topics) {
        assertThatThrownBy(() -> new PkbDeadLetterDestinationResolver(topics.get(0),
                commands(topics.get(1), topics.get(2)), events(topics.get(3), topics.get(4))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("distinct and non-blank");
    }
    private static void assertRoute(PkbDeadLetterDestinationResolver resolver, String source, String target) {
        assertThat(resolver.apply(record(source), new RuntimeException())).isEqualTo(new TopicPartition(target, 2));
    }
    private static ConsumerRecord<String, Object> record(String topic) {
        return new ConsumerRecord<>(topic, 2, 7, "key", null);
    }
    private static PkbKafkaProperties commands(String dlt, String replay) {
        return new PkbKafkaProperties(false, null, dlt, replay, null);
    }
    private static PkbEventingProperties events(String dlt, String replay) {
        return new PkbEventingProperties(false, null, 50, null, 3, null, dlt, replay);
    }
}
