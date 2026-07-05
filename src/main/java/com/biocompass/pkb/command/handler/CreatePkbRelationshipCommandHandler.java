package com.biocompass.pkb.command.handler;

import com.biocompass.pkb.command.PkbCommandNormalizer;
import com.biocompass.pkb.command.dto.CreatePkbRelationshipCommand;
import com.biocompass.pkb.command.event.PkbDomainEvent;
import com.biocompass.pkb.persistence.dao.PkbRelationshipDao;
import com.biocompass.pkb.persistence.entity.PkbRelationshipEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreatePkbRelationshipCommandHandler
        implements PkbCommandHandler<CreatePkbRelationshipCommand, PkbRelationshipEntity> {

    private final PkbRelationshipDao relationshipDao;
    private final PkbCommandNormalizer normalizer;
    private final PkbCommandItemResolver itemResolver;
    private final PkbAfterCommitEventPublisher eventPublisher;

    @Override
    public Class<CreatePkbRelationshipCommand> commandType() {
        return CreatePkbRelationshipCommand.class;
    }

    @Override
    @Transactional
    public PkbRelationshipEntity handle(CreatePkbRelationshipCommand command) {
        itemResolver.findOwnedItem(command.userId(), command.fromItemId());
        itemResolver.findOwnedItem(command.userId(), command.toItemId());

        var savedRelationship = relationshipDao.save(normalizer.toRelationshipEntity(command));
        eventPublisher.publishAfterCommit(PkbDomainEvent.relationshipCreated(
                command.userId(),
                savedRelationship.getRelationshipId(),
                command.correlationId()
        ));
        return savedRelationship;
    }
}
