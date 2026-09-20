package com.biocompass.pkb.kafka;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "biocompass.pkb.kafka")
public record PkbKafkaProperties(
        boolean commandIngressEnabled,
        String producerService,
        String deadLetterTopic,
        String replayTopic,
        Retry retry
) {

    public PkbKafkaProperties {
        producerService = textOrDefault(producerService, "pkb-service");
        deadLetterTopic = textOrDefault(deadLetterTopic, "pkb.commands.ingress.dlt.v1");
        replayTopic = textOrDefault(replayTopic, "pkb.commands.ingress.replay.v1");
        retry = retry == null ? new Retry(3, null, null, null) : retry;
    }

    private static String textOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.strip() : defaultValue;
    }

    public record Retry(
            int maxRetries,
            Duration initialInterval,
            Double multiplier,
            Duration maxInterval
    ) {

        public Retry {
            maxRetries = maxRetries < 0 ? 3 : maxRetries;
            initialInterval = initialInterval == null ? Duration.ofMillis(250) : initialInterval;
            multiplier = multiplier == null ? 2.0 : multiplier;
            maxInterval = maxInterval == null ? Duration.ofSeconds(5) : maxInterval;
        }
    }
}
