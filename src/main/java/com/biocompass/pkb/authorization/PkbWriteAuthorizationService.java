package com.biocompass.pkb.authorization;

import com.biocompass.pkb.command.dto.AssociatePkbArtifactCommand;
import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.dto.CreatePkbRelationshipCommand;
import com.biocompass.pkb.command.dto.PkbCommand;
import com.biocompass.pkb.command.dto.RegisterPkbArtifactCommand;
import com.biocompass.pkb.command.dto.SupersedePkbItemCommand;
import com.biocompass.pkb.kafka.PkbKafkaCommandContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PkbWriteAuthorizationService {

    static final String WRITE_ACTION = "write";

    private final PkbAuthorizationGateway authorizationGateway;

    public String authorize(UUID commandId, PkbCommand<?> command, PkbKafkaCommandContext context) {
        var request = new PkbAuthorizationRequest(
                commandId,
                WRITE_ACTION,
                context.producerService(),
                context.actor(),
                resource(command),
                context.priorDecisionReference()
        );
        var decision = authorizationGateway.decide(request);
        validate(decision, command.userId());
        return decision.decisionReference();
    }

    private static PkbAuthorizationRequest.Resource resource(PkbCommand<?> command) {
        return switch (command) {
            case CreatePkbItemCommand item -> resource(
                    item,
                    item.sourceType(),
                    item.consentScope(),
                    item.privacyScope(),
                    Map.of());
            case SupersedePkbItemCommand supersession -> resource(
                    supersession,
                    supersession.replacementItem().sourceType(),
                    supersession.replacementItem().consentScope(),
                    supersession.replacementItem().privacyScope(),
                    Map.of("supersededItemId", supersession.supersededItemId()));
            case CreatePkbRelationshipCommand relationship -> resource(
                    relationship,
                    null,
                    List.of(),
                    List.of(),
                    Map.of(
                            "fromItemId", relationship.fromItemId(),
                            "toItemId", relationship.toItemId()));
            case RegisterPkbArtifactCommand artifact -> resource(
                    artifact,
                    artifact.provenance().sourceKind(),
                    artifact.consentBinding() == null ? List.of() : artifact.consentBinding().consentScope(),
                    List.of(),
                    artifact.pkbItemId() == null ? Map.of() : Map.of("pkbItemId", artifact.pkbItemId()));
            case AssociatePkbArtifactCommand association -> resource(
                    association,
                    null,
                    List.of(),
                    List.of(),
                    Map.of(
                            "artifactId", association.artifactId(),
                            "pkbItemId", association.pkbItemId()));
            default -> throw new PkbAuthorizationInvalidDecisionException(
                    "Unsupported PKB command type: " + command.getClass().getName());
        };
    }

    private static PkbAuthorizationRequest.Resource resource(
            PkbCommand<?> command,
            String sourceType,
            List<String> consentScope,
            List<String> privacyScope,
            Map<String, UUID> identifiers
    ) {
        return new PkbAuthorizationRequest.Resource(
                command.userId(),
                command.getClass().getSimpleName(),
                sourceType,
                consentScope,
                privacyScope,
                identifiers);
    }

    private static void validate(PkbAuthorizationDecision decision, UUID targetUserId) {
        if (!decision.allowed()) {
            throw new PkbAuthorizationDeniedException("BioCompass AU denied the PKB write");
        }
        if (!StringUtils.hasText(decision.decisionReference())
                || !WRITE_ACTION.equals(decision.action())
                || !targetUserId.equals(decision.targetUserId())) {
            throw new PkbAuthorizationInvalidDecisionException(
                    "BioCompass AU decision is not bound to this PKB write");
        }
        if (!decision.obligations().isEmpty()) {
            throw new PkbAuthorizationInvalidDecisionException(
                    "BioCompass AU returned unsupported write obligations");
        }
    }
}
