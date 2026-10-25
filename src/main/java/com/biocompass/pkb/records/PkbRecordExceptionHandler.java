package com.biocompass.pkb.records;

import com.biocompass.pkb.authorization.PkbAuthorizationException;
import com.biocompass.pkb.records.persistence.PkbSourceRecordConflict;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=PkbRecordController.class)
public class PkbRecordExceptionHandler {
    @ExceptionHandler(PkbRecordFailure.class)
    ResponseEntity<Error> failure(PkbRecordFailure e) { return error(e.status(),e.code()); }
    @ExceptionHandler(PkbSourceRecordConflict.class)
    ResponseEntity<Error> conflict(PkbSourceRecordConflict e) { return error(409,e.code()); }
    @ExceptionHandler({MethodArgumentNotValidException.class,ConstraintViolationException.class,HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class,MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class})
    ResponseEntity<Error> invalid(Exception e) { return error(422,"invalid_request"); }
    @ExceptionHandler({DataAccessException.class,PkbAuthorizationException.class})
    ResponseEntity<Error> unavailable(Exception e) { return error(503,"unavailable"); }
    private ResponseEntity<Error> error(int status,String code) {
        return ResponseEntity.status(status).body(new Error(code,"Record operation could not be completed."));
    }
    public record Error(String code,String detail) {}
}
