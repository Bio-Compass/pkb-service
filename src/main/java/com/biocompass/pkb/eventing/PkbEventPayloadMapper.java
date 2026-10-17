package com.biocompass.pkb.eventing;

import com.biocompass.pkb.command.event.PkbDomainEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Produces the JSON-compatible outbox snapshot with the application's configured Jackson mapper.
 * This keeps the persisted payload identical in shape to the event sent to Kafka.
 */
@Component
@RequiredArgsConstructor
public class PkbEventPayloadMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public Map<String, Object> toPayload(PkbDomainEvent event) {
        return objectMapper.convertValue(event, MAP_TYPE);
    }
}
