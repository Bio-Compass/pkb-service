package com.biocompass.pkb.command.api.controller;

import com.biocompass.pkb.command.api.mapper.PkbArtifactCommandMapper;
import com.biocompass.pkb.command.api.model.AssociatePkbArtifactRequest;
import com.biocompass.pkb.command.api.model.PkbCommandAcceptedResponse;
import com.biocompass.pkb.command.api.model.RegisterPkbArtifactRequest;
import com.biocompass.pkb.command.api.service.PkbCommandIngressService;
import com.biocompass.pkb.command.api.web.CommandId;
import com.biocompass.pkb.command.api.web.CorrelationId;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pkb/commands/artifacts")
@ResponseStatus(HttpStatus.ACCEPTED)
@ApiResponse(responseCode = "202", description = "Command accepted after Kafka acknowledgement")
@RequiredArgsConstructor
public class PkbArtifactCommandController {

    private final PkbCommandIngressService commandIngressService;
    private final PkbArtifactCommandMapper commandMapper;

    @PostMapping
    public PkbCommandAcceptedResponse registerArtifact(
            @RequestParam UUID userId,
            @CommandId UUID commandId,
            @CorrelationId String correlationId,
            @Valid @RequestBody RegisterPkbArtifactRequest request
    ) {
        var command = commandMapper.toRegisterArtifactCommand(request, userId, correlationId);
        return commandIngressService.submit(commandId, correlationId, command);
    }

    @PostMapping("/{artifactId}/associations")
    public PkbCommandAcceptedResponse associateArtifact(
            @RequestParam UUID userId,
            @PathVariable UUID artifactId,
            @CommandId UUID commandId,
            @CorrelationId String correlationId,
            @Valid @RequestBody AssociatePkbArtifactRequest request
    ) {
        var command = commandMapper.toAssociateArtifactCommand(
                request,
                userId,
                artifactId,
                correlationId);
        return commandIngressService.submit(commandId, correlationId, command);
    }
}
