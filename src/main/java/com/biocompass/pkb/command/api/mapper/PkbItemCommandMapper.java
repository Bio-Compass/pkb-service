package com.biocompass.pkb.command.api.mapper;

import com.biocompass.pkb.command.api.model.CreatePkbItemRequest;
import com.biocompass.pkb.command.api.model.SupersedePkbItemRequest;
import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.dto.SupersedePkbItemCommand;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PkbItemCommandMapper {

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "supersedes", source = "supersedes")
    @Mapping(target = "correlationId", source = "correlationId")
    CreatePkbItemCommand toCreateItemCommand(
            CreatePkbItemRequest request,
            UUID userId,
            UUID supersedes,
            String correlationId
    );

    default SupersedePkbItemCommand toSupersedeItemCommand(
            SupersedePkbItemRequest request,
            UUID userId,
            UUID supersededItemId,
            String correlationId
    ) {
        var replacementItem = request.replacementItem() == null
                ? null
                : toCreateItemCommand(request.replacementItem(), userId, supersededItemId, correlationId);
        return new SupersedePkbItemCommand(userId, supersededItemId, replacementItem, correlationId);
    }
}
