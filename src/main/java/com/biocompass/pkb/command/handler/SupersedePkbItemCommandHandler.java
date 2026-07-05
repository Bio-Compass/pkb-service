package com.biocompass.pkb.command.handler;

import com.biocompass.pkb.command.dto.SupersedePkbItemCommand;
import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SupersedePkbItemCommandHandler implements PkbCommandHandler<SupersedePkbItemCommand, PkbItemEntity> {

    private final CreatePkbItemCommandHandler createItemHandler;

    @Override
    public Class<SupersedePkbItemCommand> commandType() {
        return SupersedePkbItemCommand.class;
    }

    @Override
    @Transactional
    public PkbItemEntity handle(SupersedePkbItemCommand command) {
        var replacementItem = command.replacementItem().withUserIdSupersedesAndCorrelationId(
                command.userId(),
                command.supersededItemId(),
                command.correlationId()
        );
        return createItemHandler.handle(replacementItem);
    }
}
