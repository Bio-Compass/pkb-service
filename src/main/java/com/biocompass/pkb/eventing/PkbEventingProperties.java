package com.biocompass.pkb.eventing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "biocompass.pkb.eventing")
public record PkbEventingProperties(
        boolean enabled,
        Duration relayInterval,
        int relayBatchSize,
        String consumerName,
        int topicPartitions,
        Duration publishTimeout,
        String deadLetterTopic,
        String replayTopic
) {

    public PkbEventingProperties {
        relayInterval = relayInterval == null ? Duration.ofSeconds(1) : relayInterval;
        relayBatchSize = relayBatchSize <= 0 ? 50 : relayBatchSize;
        consumerName = consumerName == null || consumerName.isBlank() ? "pkb-enrichment" : consumerName;
        topicPartitions = topicPartitions <= 0 ? 3 : topicPartitions;
        publishTimeout = publishTimeout == null || publishTimeout.isZero() || publishTimeout.isNegative()
                ? Duration.ofSeconds(5)
                : publishTimeout;
        deadLetterTopic = deadLetterTopic == null || deadLetterTopic.isBlank()
                ? "pkb.events.dlt.v1" : deadLetterTopic.strip();
        replayTopic = replayTopic == null || replayTopic.isBlank()
                ? "pkb.events.replay.v1" : replayTopic.strip();
    }

}
