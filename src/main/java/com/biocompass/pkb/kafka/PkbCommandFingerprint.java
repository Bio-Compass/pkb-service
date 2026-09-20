package com.biocompass.pkb.kafka;

import com.biocompass.pkb.command.dto.PkbCommand;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.kafka.support.JacksonMapperUtils;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;

@Component
public class PkbCommandFingerprint {

    private final tools.jackson.databind.ObjectWriter writer = JacksonMapperUtils.enhancedJsonMapper()
            .rebuild()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build()
            .writer();

    public String hash(PkbCommand<?> command) {
        try {
            return HexFormat.of().formatHex(sha256(writer.writeValueAsBytes(command)));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to fingerprint PKB command", exception);
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
