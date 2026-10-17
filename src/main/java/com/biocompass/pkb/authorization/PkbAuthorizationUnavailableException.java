package com.biocompass.pkb.authorization;

public class PkbAuthorizationUnavailableException extends PkbAuthorizationException {

    public PkbAuthorizationUnavailableException(String message) {
        super(message);
    }

    public PkbAuthorizationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
