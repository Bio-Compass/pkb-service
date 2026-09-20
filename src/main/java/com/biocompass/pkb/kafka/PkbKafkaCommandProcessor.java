package com.biocompass.pkb.kafka;

import com.biocompass.pkb.authorization.PkbWriteAuthorizationService;
import com.biocompass.pkb.command.PkbCommandService;
import com.biocompass.pkb.command.PkbCommandValidator;
import com.biocompass.pkb.persistence.entity.PkbProcessedCommandEntity;
import com.biocompass.pkb.persistence.repository.PkbProcessedCommandRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PkbKafkaCommandProcessor {

    private final PkbProcessedCommandRepository processedCommandRepository;
    private final PkbCommandService commandService;
    private final PkbWriteAuthorizationService authorizationService;
    private final PkbCommandFingerprint commandFingerprint;
    private final PkbCommandValidator commandValidator;

    /**
     * The inbox insert and the canonical PKB write share one database transaction. A failed command therefore
     * remains eligible for Kafka retry, while a redelivered command that committed successfully is a no-op.
     */
    @Transactional
    public boolean process(PkbKafkaCommandMessage message) {
        commandValidator.validate(message);
        var command = message.command();
        var commandType = command.getClass().getSimpleName();
        var payloadHash = commandFingerprint.hash(command);

        var processed = processedCommandRepository.findById(message.commandId());
        if (processed.isPresent()) {
            recordExactRetry(processed.orElseThrow(), commandType, command.userId(), payloadHash);
            return false;
        }

        var decisionReference = authorizationService.authorize(message.commandId(), command, message.context());
        boolean firstSuccessfulDelivery = processedCommandRepository.insertIfAbsent(
                message.commandId(),
                commandType,
                command.userId(),
                payloadHash,
                message.context().producerService(),
                message.context().actor().actorId(),
                message.context().actor().userId(),
                message.context().actor().purposeOfUse(),
                message.context().priorDecisionReference(),
                decisionReference,
                command.correlationId()
        ) == 1;
        if (!firstSuccessfulDelivery) {
            var concurrentlyProcessed = processedCommandRepository.findById(message.commandId()).orElseThrow();
            recordExactRetry(concurrentlyProcessed, commandType, command.userId(), payloadHash);
            return false;
        }

        commandService.handle(command);
        return true;
    }

    private void recordExactRetry(
            PkbProcessedCommandEntity processed,
            String commandType,
            UUID userId,
            String payloadHash
    ) {
        if (!processed.getCommandType().equals(commandType)
                || !processed.getUserId().equals(userId)
                || !processed.getPayloadHash().equals(payloadHash)) {
            throw new PkbCommandIdReuseException(
                    "PKB command ID %s was already used for a different user, type, or payload"
                            .formatted(processed.getCommandId()));
        }
        processedCommandRepository.recordDuplicate(processed.getCommandId());
    }

}
