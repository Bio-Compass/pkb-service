package com.biocompass.pkb.eventing;

public class PkbKafkaPublicationException extends RuntimeException {

    public PkbKafkaPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
