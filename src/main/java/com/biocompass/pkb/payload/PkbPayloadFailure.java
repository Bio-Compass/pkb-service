package com.biocompass.pkb.payload;

/** Safe structural/transport error; contains no payload values or parser excerpts. */
public final class PkbPayloadFailure extends RuntimeException {
    private final int status;
    private final String code;
    public PkbPayloadFailure(int status, String code) {
        super("PKB JSON contract rejected");
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
}
