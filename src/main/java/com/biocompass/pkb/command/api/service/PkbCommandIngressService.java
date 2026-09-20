package com.biocompass.pkb.command.api.service;

import com.biocompass.pkb.authorization.PkbAuthorizationRequest;
import com.biocompass.pkb.authorization.PkbWriteAuthorizationService;
import com.biocompass.pkb.command.api.model.PkbCommandAcceptedResponse;
import com.biocompass.pkb.command.PkbCommandValidator;
import com.biocompass.pkb.command.dto.PkbCommand;
import com.biocompass.pkb.kafka.PkbCommandSubmissionGateway;
import com.biocompass.pkb.kafka.PkbKafkaCommandContext;
import com.biocompass.pkb.kafka.PkbKafkaCommandPublicationException;
import com.biocompass.pkb.kafka.PkbKafkaProperties;
import com.biocompass.pkb.security.BioCompassActorAuthenticationResolver;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PkbCommandIngressService {

    private final PkbCommandSubmissionGateway commandSubmissionGateway;
    private final PkbCommandValidator commandValidator;
    private final PkbWriteAuthorizationService authorizationService;
    private final BioCompassActorAuthenticationResolver actorResolver;
    private final PkbKafkaProperties kafkaProperties;

    public PkbCommandAcceptedResponse submit(UUID commandId, String correlationId, PkbCommand<?> command) {
        if (!kafkaProperties.commandIngressEnabled()) {
            throw new PkbKafkaCommandPublicationException("PKB command ingress is disabled");
        }
        commandValidator.validate(command);
        var actor = actorResolver.resolve();
        var authorizationActor = new PkbAuthorizationRequest.Actor(
                actor.actorId(),
                actor.userId(),
                actor.staff(),
                actor.roles(),
                actor.scopes(),
                actor.purposeOfUse());
        var context = new PkbKafkaCommandContext(kafkaProperties.producerService(), authorizationActor, null);
        var decisionReference = authorizationService.authorize(commandId, command, context);
        commandSubmissionGateway.submit(commandId, command, context.withPriorDecisionReference(decisionReference));
        return PkbCommandAcceptedResponse.accepted(commandId, correlationId);
    }
}
