package com.biocompass.pkb.command.api.controller;

import com.biocompass.pkb.command.api.mapper.PkbRelationshipCommandMapper;
import com.biocompass.pkb.command.api.model.CreatePkbRelationshipRequest;
import com.biocompass.pkb.command.api.model.PkbCommandAcceptedResponse;
import com.biocompass.pkb.command.api.service.PkbCommandIngressService;
import com.biocompass.pkb.command.api.web.CommandId;
import com.biocompass.pkb.command.api.web.CorrelationId;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pkb/commands/relationships")
@ResponseStatus(HttpStatus.ACCEPTED)
@ApiResponse(responseCode = "202", description = "Command accepted after Kafka acknowledgement")
@RequiredArgsConstructor
public class PkbRelationshipCommandController {

    private final PkbCommandIngressService commandIngressService;
    private final PkbRelationshipCommandMapper commandMapper;

    @PostMapping
    public PkbCommandAcceptedResponse createRelationship(
            @RequestParam UUID userId,
            @CommandId UUID commandId,
            @CorrelationId String correlationId,
            @Valid @RequestBody CreatePkbRelationshipRequest request
    ) {
        var command = commandMapper.toCreateRelationshipCommand(request, userId, correlationId);
        return commandIngressService.submit(commandId, correlationId, command);
    }
}
