package com.biocompass.pkb.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.dto.PkbProvenanceCommand;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = PkbCommandFingerprintTest.TestApplication.class)
class PkbCommandFingerprintTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private PkbCommandFingerprint fingerprint;

    @Test
    void canonicalizesMapOrderButDetectsAnyPayloadChange() {
        var firstOrder = new LinkedHashMap<String, Object>();
        firstOrder.put("amount", 350);
        firstOrder.put("unit", "ml");
        var reverseOrder = new LinkedHashMap<String, Object>();
        reverseOrder.put("unit", "ml");
        reverseOrder.put("amount", 350);

        var firstHash = fingerprint.hash(command(firstOrder));

        assertThat(fingerprint.hash(command(reverseOrder))).isEqualTo(firstHash);
        assertThat(fingerprint.hash(command(Map.of("amount", 351, "unit", "ml")))).isNotEqualTo(firstHash);
        assertThat(firstHash).matches("[0-9a-f]{64}");
    }

    private static CreatePkbItemCommand command(Map<String, Object> payload) {
        return new CreatePkbItemCommand(
                USER_ID,
                "nutrition_intake",
                "water",
                "active",
                payload,
                "manual",
                "water-1",
                null,
                null,
                null,
                "en",
                null,
                null,
                "verified",
                null,
                new PkbProvenanceCommand("manual", "user", null, null, null),
                "correlation-1");
    }

    @SpringBootConfiguration
    @Import(PkbCommandFingerprint.class)
    static class TestApplication {
    }
}
