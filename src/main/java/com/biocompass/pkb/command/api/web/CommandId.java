package com.biocompass.pkb.command.api.web;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(
        name = "X-Command-Id",
        description = "Immutable idempotency key for this PKB command.",
        in = ParameterIn.HEADER,
        required = true
)
public @interface CommandId {
}
