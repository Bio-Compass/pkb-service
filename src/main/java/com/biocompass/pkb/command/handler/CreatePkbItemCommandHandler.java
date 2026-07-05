package com.biocompass.pkb.command.handler;

import com.biocompass.pkb.command.PkbCommandNormalizer;
import com.biocompass.pkb.command.PkbCommandValidationException;
import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.event.PkbDomainEvent;
import com.biocompass.pkb.persistence.dao.PkbItemDao;
import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreatePkbItemCommandHandler implements PkbCommandHandler<CreatePkbItemCommand, PkbItemEntity> {

    private final PkbItemDao itemDao;
    private final PkbCommandNormalizer normalizer;
    private final PkbCommandItemResolver itemResolver;
    private final PkbAfterCommitEventPublisher eventPublisher;

    @Override
    public Class<CreatePkbItemCommand> commandType() {
        return CreatePkbItemCommand.class;
    }

    @Override
    @Transactional
    public PkbItemEntity handle(CreatePkbItemCommand command) {
        var supersededItem = itemResolver.findOptionalOwnedItem(command.userId(), command.supersedes())
                .map(this::rejectAlreadySuperseded)
                .orElse(null);

        var savedItem = itemDao.saveWithProvenance(
                normalizer.toItemEntity(command),
                normalizer.toProvenanceEntity(command.provenance())
        );

        if (supersededItem != null) {
            supersededItem.setSupersededBy(savedItem.getPkbItemId());
            itemDao.save(supersededItem);
            eventPublisher.publishAfterCommit(PkbDomainEvent.itemSupersessionLinked(
                    command.userId(),
                    supersededItem.getPkbItemId(),
                    savedItem.getPkbItemId(),
                    command.correlationId()
            ));
        }

        eventPublisher.publishAfterCommit(PkbDomainEvent.itemCreated(
                command.userId(),
                savedItem.getPkbItemId(),
                command.correlationId()
        ));
        return savedItem;
    }

    private PkbItemEntity rejectAlreadySuperseded(PkbItemEntity supersededItem) {
        if (supersededItem.getSupersededBy() != null) {
            throw new PkbCommandValidationException("PKB item is already superseded");
        }
        return supersededItem;
    }
}
