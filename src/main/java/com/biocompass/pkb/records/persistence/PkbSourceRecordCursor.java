package com.biocompass.pkb.records.persistence;

import java.time.Instant;
import java.util.UUID;

public record PkbSourceRecordCursor(Instant observedAt, Instant recordedAt, UUID itemId) {
}
