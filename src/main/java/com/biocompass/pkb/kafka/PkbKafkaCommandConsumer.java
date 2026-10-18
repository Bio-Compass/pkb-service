package com.biocompass.pkb.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PkbKafkaCommandConsumer {

    private final PkbKafkaCommandProcessor commandProcessor;

    @KafkaListener(
            topics = {
                    "${spring.kafka.template.default-topic}",
                    "${biocompass.pkb.kafka.replay-topic:pkb.commands.ingress.replay.v1}"
            },
            autoStartup = "${biocompass.pkb.kafka.command-ingress-enabled:false}"
    )
    public void consume(PkbKafkaCommandMessage message) {
        commandProcessor.process(message);
    }
}
