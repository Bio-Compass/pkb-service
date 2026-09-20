package com.biocompass.pkb.kafka;

import com.biocompass.pkb.command.dto.PkbCommand;
import java.util.UUID;

@FunctionalInterface
public interface PkbCommandSubmissionGateway {

    void submit(UUID commandId, PkbCommand<?> command, PkbKafkaCommandContext context);
}
