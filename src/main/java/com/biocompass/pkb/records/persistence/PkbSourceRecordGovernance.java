package com.biocompass.pkb.records.persistence;

import java.util.List;

public record PkbSourceRecordGovernance(String sourceType, List<String> privacyScope, List<String> consentScope) {
    public PkbSourceRecordGovernance {
        privacyScope = List.copyOf(privacyScope);
        consentScope = List.copyOf(consentScope);
    }
}
