package com.biocompass.pkb.records.persistence;

import java.util.List;
import java.util.UUID;

/** Actual contributing item attributes supplied to AU before aggregate disclosure. */
public record PkbSourceRecordAccess(UUID itemId, String sourceType, List<String> privacyScope,
                                    List<String> consentScope) {
    public PkbSourceRecordAccess {
        privacyScope = List.copyOf(privacyScope);
        consentScope = List.copyOf(consentScope);
    }
}
