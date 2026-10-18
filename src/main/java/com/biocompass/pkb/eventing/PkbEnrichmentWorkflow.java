package com.biocompass.pkb.eventing;

import com.biocompass.pkb.command.event.PkbDomainEvent;

/**
 * Extension point for OCR, extraction, embeddings, summarization, and terminology mapping.
 * Implementations must persist derived data as canonical records with provenance, never alter the
 * source artifact object or its metadata.
 */
@FunctionalInterface
public interface PkbEnrichmentWorkflow {

    void handleCompleted(PkbDomainEvent event);
}
