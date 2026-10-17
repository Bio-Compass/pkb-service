package com.biocompass.pkb.kafka;

import com.biocompass.pkb.command.dto.PkbCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record PkbKafkaCommandMessage(
        @NotNull UUID commandId,
        @NotNull Instant submittedAt,
        @Valid @NotNull PkbKafkaCommandContext context,
        @Valid @NotNull PkbCommand<?> command
) {}
