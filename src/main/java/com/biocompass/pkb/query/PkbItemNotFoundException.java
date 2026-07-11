package com.biocompass.pkb.query;

public class PkbItemNotFoundException extends RuntimeException {

    public PkbItemNotFoundException() {
        super("PKB item was not found.");
    }
}
