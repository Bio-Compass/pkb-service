package com.biocompass.pkb.command;

import java.util.List;

public class PkbCommandValidationException extends RuntimeException {

    public PkbCommandValidationException(String message) {
        super(message);
    }

    public PkbCommandValidationException(List<String> errors) {
        super(String.join("; ", errors));
    }
}
