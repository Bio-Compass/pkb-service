package com.biocompass.pkb.command.api.controller;

import com.biocompass.pkb.authorization.PkbAuthorizationDeniedException;
import com.biocompass.pkb.authorization.PkbAuthorizationInvalidDecisionException;
import com.biocompass.pkb.authorization.PkbAuthorizationUnavailableException;
import com.biocompass.pkb.command.PkbCommandValidationException;
import com.biocompass.pkb.command.api.model.PkbCommandIngressErrorResponse;
import com.biocompass.pkb.kafka.PkbKafkaCommandPublicationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        PkbItemCommandController.class,
        PkbRelationshipCommandController.class,
        PkbArtifactCommandController.class
})
public class PkbCommandIngressExceptionHandler {

    @ExceptionHandler(PkbCommandValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public PkbCommandIngressErrorResponse handleValidation(PkbCommandValidationException exception) {
        return new PkbCommandIngressErrorResponse("pkb_invalid_command", exception.getMessage());
    }

    @ExceptionHandler(PkbKafkaCommandPublicationException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public PkbCommandIngressErrorResponse handleKafkaUnavailable(PkbKafkaCommandPublicationException exception) {
        return new PkbCommandIngressErrorResponse("pkb_command_not_accepted", exception.getMessage());
    }

    @ExceptionHandler(PkbAuthorizationDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public PkbCommandIngressErrorResponse handleAuthorizationDenied(PkbAuthorizationDeniedException exception) {
        return new PkbCommandIngressErrorResponse("pkb_write_denied", exception.getMessage());
    }

    @ExceptionHandler({
            PkbAuthorizationUnavailableException.class,
            PkbAuthorizationInvalidDecisionException.class
    })
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public PkbCommandIngressErrorResponse handleAuthorizationFailure(RuntimeException exception) {
        return new PkbCommandIngressErrorResponse("pkb_authorization_unavailable", exception.getMessage());
    }
}
