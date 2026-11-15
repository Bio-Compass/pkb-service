package com.biocompass.pkb.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PkbBoundedJsonDeserializerTest {
    @Test void kafkaAdmissionRejectsNonUtf8Bytes() {
        var reader = new PkbBoundedJsonDeserializer();
        assertThatThrownBy(() -> reader.deserialize("commands", new byte[]{'"', (byte)0xc0, (byte)0xaf, '"'}))
                .isInstanceOf(com.biocompass.pkb.payload.PkbPayloadFailure.class).hasMessage("PKB JSON contract rejected");
    }

    @Test void parserFailureDoesNotExposeRawDataInItsExceptionChain() {
        var reader = new PkbBoundedJsonDeserializer();
        reader.configure(Map.of("spring.json.value.default.type", PkbKafkaCommandMessage.class.getName(),
                "spring.json.use.type.headers", false), false);
        var bytes = "{\"private-fixture\":private-secret}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> reader.deserialize("commands", new org.apache.kafka.common.header.internals.RecordHeaders(), bytes))
                .isInstanceOf(org.apache.kafka.common.errors.SerializationException.class)
                .hasNoCause().hasMessage("Invalid PKB JSON message");
    }
}
