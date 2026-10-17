package com.biocompass.pkb.eventing;

import com.biocompass.pkb.command.event.PkbDomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PkbEventPayloadMapperTest {

    private final PkbEventPayloadMapper mapper = new PkbEventPayloadMapper(
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    );

    @Test
    void producesTheKafkaCompatibleJsonPayload() {
        var userId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        var artifactId = UUID.randomUUID();
        var event = PkbDomainEvent.enrichmentRequested(userId, artifactId, itemId, "workflow-1");

        assertThat(mapper.toPayload(event)).containsAllEntriesOf(java.util.Map.ofEntries(
                java.util.Map.entry("eventId", event.eventId().toString()),
                java.util.Map.entry("eventType", "pkb.enrichment.requested"),
                java.util.Map.entry("occurredAt", event.occurredAt().toString()),
                java.util.Map.entry("userId", userId.toString()),
                java.util.Map.entry("pkbItemId", itemId.toString()),
                java.util.Map.entry("artifactId", artifactId.toString()),
                java.util.Map.entry("correlationId", "workflow-1")
        ));
        assertThat(mapper.toPayload(event))
                .containsEntry("relatedPkbItemId", null)
                .containsEntry("relationshipId", null);
    }
}
