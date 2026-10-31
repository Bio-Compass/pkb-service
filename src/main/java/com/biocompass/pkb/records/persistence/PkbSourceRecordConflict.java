package com.biocompass.pkb.records.persistence;

public class PkbSourceRecordConflict extends RuntimeException {
    private final String code;

    public PkbSourceRecordConflict(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
