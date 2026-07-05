package com.biocompass.pkb.command.dto;

import java.util.UUID;

public interface PkbCommand<R> {

    UUID userId();

    String correlationId();
}
