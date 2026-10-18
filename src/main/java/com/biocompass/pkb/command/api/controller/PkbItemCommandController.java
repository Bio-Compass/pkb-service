package com.biocompass.pkb.command.api.controller;

import com.biocompass.pkb.command.api.mapper.PkbItemCommandMapper;
import com.biocompass.pkb.command.api.model.CreatePkbItemRequest;
import com.biocompass.pkb.command.api.model.PkbCommandAcceptedResponse;
import com.biocompass.pkb.command.api.model.SupersedePkbItemRequest;
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
@RequestMapping("/api/pkb/commands/items")
@ResponseStatus(HttpStatus.ACCEPTED)
@ApiResponse(responseCode = "202", description = "Command accepted after Kafka acknowledgement")
@RequiredArgsConstructor
public class PkbItemCommandController {

    private final PkbCommandIngressService commandIngressService;
    private final PkbItemCommandMapper commandMapper;

    @PostMapping
    public PkbCommandAcceptedResponse createItem(
            @RequestParam UUID userId,
            @CommandId UUID commandId,
            @CorrelationId String correlationId,
            @Valid @RequestBody CreatePkbItemRequest request
    ) {
        var command = commandMapper.toCreateItemCommand(request, userId, null, correlationId);
        return commandIngressService.submit(commandId, correlationId, command);
    }

    @PostMapping("/{itemId}/supersessions")
    public PkbCommandAcceptedResponse supersedeItem(
            @RequestParam UUID userId,
            @PathVariable UUID itemId,
            @CommandId UUID commandId,
            @CorrelationId String correlationId,
            @Valid @RequestBody SupersedePkbItemRequest request
    ) {
        var command = commandMapper.toSupersedeItemCommand(request, userId, itemId, correlationId);
        return commandIngressService.submit(commandId, correlationId, command);
    }
}
