package com.biocompass.pkb.command.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "commandType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CreatePkbItemCommand.class, name = "create-item"),
        @JsonSubTypes.Type(value = SupersedePkbItemCommand.class, name = "supersede-item"),
        @JsonSubTypes.Type(value = CreatePkbRelationshipCommand.class, name = "create-relationship"),
        @JsonSubTypes.Type(value = RegisterPkbArtifactCommand.class, name = "register-artifact"),
        @JsonSubTypes.Type(value = AssociatePkbArtifactCommand.class, name = "associate-artifact")
})
public interface PkbCommand<R> {

    UUID userId();

    String correlationId();
}
