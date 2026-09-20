package com.biocompass.pkb.command.api.mapper;

import com.biocompass.pkb.command.api.model.AssociatePkbArtifactRequest;
import com.biocompass.pkb.command.api.model.RegisterPkbArtifactRequest;
import com.biocompass.pkb.command.dto.AssociatePkbArtifactCommand;
import com.biocompass.pkb.command.dto.RegisterPkbArtifactCommand;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PkbArtifactCommandMapper {

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "correlationId", source = "correlationId")
    RegisterPkbArtifactCommand toRegisterArtifactCommand(
            RegisterPkbArtifactRequest request,
            UUID userId,
            String correlationId
    );

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "artifactId", source = "artifactId")
    @Mapping(target = "correlationId", source = "correlationId")
    AssociatePkbArtifactCommand toAssociateArtifactCommand(
            AssociatePkbArtifactRequest request,
            UUID userId,
            UUID artifactId,
            String correlationId
    );
}
