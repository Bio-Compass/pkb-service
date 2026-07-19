package com.biocompass.pkb.query;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PkbItemQueryController.class)
public class PkbItemQueryExceptionHandler {

    @ExceptionHandler(PkbItemNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public PkbQueryErrorResponse handleNotFound(PkbItemNotFoundException exception) {
        return new PkbQueryErrorResponse("pkb_item_not_found", exception.getMessage());
    }

    @ExceptionHandler(PkbAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public PkbQueryErrorResponse handleAccessDenied(PkbAccessDeniedException exception) {
        return new PkbQueryErrorResponse("pkb_access_denied", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public PkbQueryErrorResponse handleInvalidQuery(IllegalArgumentException exception) {
        return new PkbQueryErrorResponse("pkb_invalid_query", exception.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public PkbQueryErrorResponse handleConstraintViolation(ConstraintViolationException exception) {
        return new PkbQueryErrorResponse("pkb_invalid_query", exception.getMessage());
    }
}
