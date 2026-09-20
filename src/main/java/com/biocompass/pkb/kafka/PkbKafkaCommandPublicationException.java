package com.biocompass.pkb.kafka;

public class PkbKafkaCommandPublicationException extends RuntimeException {

    public PkbKafkaCommandPublicationException(String message) {
        super(message);
    }

    public PkbKafkaCommandPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
