package com.biocompass.pkb.kafka;

import com.biocompass.pkb.command.PkbCommandValidator;
import com.biocompass.pkb.command.dto.PkbCommand;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PkbKafkaCommandSubmissionService implements PkbCommandSubmissionGateway {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PkbCommandValidator commandValidator;

    @Override
    public void submit(UUID commandId, PkbCommand<?> command, PkbKafkaCommandContext context) {
        commandValidator.validate(command);
        var message = new PkbKafkaCommandMessage(commandId, Instant.now(), context, command);
        if (com.biocompass.pkb.payload.PkbPayload.mapper().writeValueAsBytes(message).length
                > com.biocompass.pkb.payload.PkbPayload.MAX_COMMAND_BYTES) {
            throw new com.biocompass.pkb.payload.PkbPayloadFailure(413, "payload_too_large");
        }
        try {
            kafkaTemplate.sendDefault(command.userId().toString(), message).join();
        } catch (KafkaException | CompletionException exception) {
            throw new PkbKafkaCommandPublicationException("Kafka did not acknowledge the PKB command", exception);
        }
    }

}
