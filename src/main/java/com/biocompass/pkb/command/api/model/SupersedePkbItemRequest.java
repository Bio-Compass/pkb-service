package com.biocompass.pkb.command.api.model;

import jakarta.validation.Valid;

@io.swagger.v3.oas.annotations.media.Schema(additionalProperties=io.swagger.v3.oas.annotations.media.Schema.AdditionalPropertiesValue.FALSE)
public record SupersedePkbItemRequest(@Valid CreatePkbItemRequest replacementItem) {
}
