package com.biocompass.pkb.authorization;

public class PkbAuthorizationInvalidDecisionException extends PkbAuthorizationException {

    public PkbAuthorizationInvalidDecisionException(String message) {
        super(message);
    }

    public PkbAuthorizationInvalidDecisionException(String message, Throwable cause) {
        super(message, cause);
    }
}
