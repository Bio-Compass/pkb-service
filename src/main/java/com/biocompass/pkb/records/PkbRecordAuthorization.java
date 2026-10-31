package com.biocompass.pkb.records;

import com.biocompass.pkb.authorization.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class PkbRecordAuthorization {
    private final PkbAuthorizationGateway gateway;
    public String authorize(PkbRecordAuthority authority, String action, String entityType, String subtype,
            String sourceType, List<String> privacy, List<String> consent, UUID itemId) {
        var actor = new PkbAuthorizationRequest.Actor(authority.actorId(), authority.userId(), false, Set.of(),
                authority.permissions(), authority.purposeOfUse());
        var resource = new PkbAuthorizationRequest.Resource(authority.userId(), "source_record", sourceType,
                consent, privacy, itemId == null ? Map.of() : Map.of("item_id", itemId),
                Map.of("source_system", authority.producer(), "entity_type", entityType, "subtype", subtype));
        var decision = gateway.decide(new PkbAuthorizationRequest(UUID.randomUUID(), action, authority.producer(), actor, resource, null));
        if (decision == null) throw new PkbRecordFailure(503, "unavailable");
        if (!decision.allowed()) throw new PkbRecordFailure(403, "forbidden");
        if (!action.equals(decision.action()) || !authority.userId().equals(decision.targetUserId())
                || decision.decisionReference() == null || decision.decisionReference().isBlank()
                || !decision.obligations().isEmpty()) throw new PkbRecordFailure(503, "unavailable");
        return decision.decisionReference();
    }
}
