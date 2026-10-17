package com.biocompass.pkb.command.api.mapper;

import com.biocompass.pkb.command.api.model.CreatePkbRelationshipRequest;
import com.biocompass.pkb.command.dto.CreatePkbRelationshipCommand;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PkbRelationshipCommandMapper {

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "correlationId", source = "correlationId")
    CreatePkbRelationshipCommand toCreateRelationshipCommand(
            CreatePkbRelationshipRequest request,
            UUID userId,
            String correlationId
    );
}
