package com.biocompass.pkb.command.event;

public enum PkbDomainEventType {
    ITEM_CREATED("pkb.item.created"),
    ITEM_SUPERSESSION_LINKED("pkb.item.supersession-linked"),
    RELATIONSHIP_CREATED("pkb.relationship.created"),
    ARTIFACT_ASSOCIATED("pkb.artifact.associated");

    private final String value;

    PkbDomainEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
