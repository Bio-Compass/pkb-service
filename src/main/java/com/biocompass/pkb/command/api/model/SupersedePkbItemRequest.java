package com.biocompass.pkb.command.api.model;

import jakarta.validation.Valid;

public record SupersedePkbItemRequest(@Valid CreatePkbItemRequest replacementItem) {
}
