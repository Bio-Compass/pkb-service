package com.biocompass.pkb.command.handler;

import com.biocompass.pkb.command.PkbCommandNotFoundException;
import com.biocompass.pkb.command.PkbCommandValidationException;
import com.biocompass.pkb.command.dto.AssociatePkbArtifactCommand;
import com.biocompass.pkb.command.event.PkbDomainEvent;
import com.biocompass.pkb.persistence.dao.PkbArtifactDao;
import com.biocompass.pkb.persistence.entity.PkbArtifactEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AssociatePkbArtifactCommandHandler
        implements PkbCommandHandler<AssociatePkbArtifactCommand, PkbArtifactEntity> {

    private final PkbArtifactDao artifactDao;
    private final PkbCommandItemResolver itemResolver;
    private final PkbAfterCommitEventPublisher eventPublisher;

    @Override
    public Class<AssociatePkbArtifactCommand> commandType() {
        return AssociatePkbArtifactCommand.class;
    }

    @Override
    @Transactional
    public PkbArtifactEntity handle(AssociatePkbArtifactCommand command) {
        itemResolver.requireOwnedItem(command.userId(), command.pkbItemId());

        var artifact = artifactDao.findByUserAndArtifactId(command.userId(), command.artifactId())
                .orElseThrow(() -> new PkbCommandNotFoundException("PKB artifact", command.artifactId()));

        if (command.pkbItemId().equals(artifact.getPkbItemId())) {
            return artifact;
        }
        if (artifact.getPkbItemId() != null) {
            throw new PkbCommandValidationException("artifact is already associated with another PKB item");
        }

        artifact.setPkbItemId(command.pkbItemId());
        var savedArtifact = artifactDao.save(artifact);
        eventPublisher.publishAfterCommit(PkbDomainEvent.artifactAssociated(
                command.userId(),
                savedArtifact.getArtifactId(),
                command.pkbItemId(),
                command.correlationId()
        ));
        return savedArtifact;
    }
}
