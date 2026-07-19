package com.biocompass.pkb.command.handler;

import com.biocompass.pkb.artifact.PkbArtifactObjectKeyGenerator;
import com.biocompass.pkb.command.PkbCommandNormalizer;
import com.biocompass.pkb.command.PkbCommandValidationException;
import com.biocompass.pkb.command.dto.RegisterPkbArtifactCommand;
import com.biocompass.pkb.command.event.PkbDomainEvent;
import com.biocompass.pkb.persistence.dao.PkbArtifactDao;
import com.biocompass.pkb.persistence.dao.PkbArtifactProvenanceDao;
import com.biocompass.pkb.persistence.dao.PkbConsentBindingDao;
import com.biocompass.pkb.persistence.entity.PkbArtifactEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RegisterPkbArtifactCommandHandler
        implements PkbCommandHandler<RegisterPkbArtifactCommand, PkbArtifactEntity> {

    private final PkbArtifactDao artifactDao;
    private final PkbArtifactProvenanceDao artifactProvenanceDao;
    private final PkbConsentBindingDao consentBindingDao;
    private final PkbCommandNormalizer normalizer;
    private final PkbCommandItemResolver itemResolver;
    private final PkbArtifactObjectKeyGenerator objectKeyGenerator;
    private final PkbAfterCommitEventPublisher eventPublisher;

    @Override
    public Class<RegisterPkbArtifactCommand> commandType() {
        return RegisterPkbArtifactCommand.class;
    }

    @Override
    @Transactional
    public PkbArtifactEntity handle(RegisterPkbArtifactCommand command) {
        if (command.pkbItemId() != null) {
            itemResolver.requireOwnedItem(command.userId(), command.pkbItemId());
        }

        var objectKey = objectKeyGenerator.documentObjectKey(
                command.userId(),
                command.documentId(),
                command.objectName()
        );
        if (artifactDao.findByObjectKey(command.userId(), objectKey).isPresent()) {
            throw new PkbCommandValidationException("artifact object key is already registered");
        }

        var savedArtifact = artifactDao.save(normalizer.toArtifactEntity(command, objectKey));
        artifactProvenanceDao.save(normalizer.toArtifactProvenanceEntity(
                command.provenance(),
                command.userId(),
                savedArtifact.getArtifactId()
        ));
        if (command.consentBinding() != null) {
            consentBindingDao.save(normalizer.toArtifactConsentBindingEntity(
                    command.consentBinding(),
                    command.userId(),
                    savedArtifact.getArtifactId()
            ));
        }

        eventPublisher.publishAfterCommit(PkbDomainEvent.artifactCreated(
                command.userId(),
                savedArtifact.getArtifactId(),
                command.pkbItemId(),
                command.correlationId()
        ));
        if (command.requestEnrichment()) {
            eventPublisher.publishAfterCommit(PkbDomainEvent.enrichmentRequested(
                    command.userId(),
                    savedArtifact.getArtifactId(),
                    command.pkbItemId(),
                    command.correlationId()
            ));
        }

        return savedArtifact;
    }
}
