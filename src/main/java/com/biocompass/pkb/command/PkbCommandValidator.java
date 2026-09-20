package com.biocompass.pkb.command;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PkbCommandValidator {

    private final Validator validator;

    public void validate(Object value) {
        var errors = validator.validate(value).stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(this::format)
                .toList();
        if (!errors.isEmpty()) {
            throw new PkbCommandValidationException(errors);
        }
    }

    private String format(ConstraintViolation<?> violation) {
        return "%s %s".formatted(violation.getPropertyPath(), violation.getMessage());
    }
}
