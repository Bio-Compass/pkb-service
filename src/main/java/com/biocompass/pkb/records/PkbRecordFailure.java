package com.biocompass.pkb.records;

/** Public failures contain only a stable code, never upstream responses or credentials. */
public final class PkbRecordFailure extends RuntimeException {
    private final int status;
    private final String code;
    public PkbRecordFailure(int status, String code) {
        super(code); this.status = status; this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
}
