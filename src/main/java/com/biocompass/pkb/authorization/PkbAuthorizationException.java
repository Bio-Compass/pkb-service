package com.biocompass.pkb.authorization;

public abstract class PkbAuthorizationException extends RuntimeException {

    protected PkbAuthorizationException(String message) {
        super(message);
    }

    protected PkbAuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
