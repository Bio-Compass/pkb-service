package com.biocompass.pkb.command.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PkbDomainEventType {
    ITEM_CREATED("pkb.item.created"),
    ITEM_SUPERSESSION_LINKED("pkb.item.supersession-linked"),
    RELATIONSHIP_CREATED("pkb.relationship.created"),
    ARTIFACT_CREATED("pkb.artifact.created"),
    ARTIFACT_ASSOCIATED("pkb.artifact.associated"),
    ENRICHMENT_REQUESTED("pkb.enrichment.requested"),
    ENRICHMENT_COMPLETED("pkb.enrichment.completed");

    private final String value;

    PkbDomainEventType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static PkbDomainEventType fromValue(String value) {
        for (var eventType : values()) {
            if (eventType.value.equals(value)) {
                return eventType;
            }
        }
        throw new IllegalArgumentException("Unsupported PKB event type: " + value);
    }
}
