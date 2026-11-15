package com.biocompass.pkb.payload;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** An immutable, bounded JSON object; never interpreted as authority. */
public final class PkbPayload {
    public static final int MAX_BYTES = 64 * 1024;
    public static final int MAX_REQUEST_BYTES = 128 * 1024;
    public static final int MAX_COMMAND_BYTES = 256 * 1024;
    public static final int MAX_HISTORY_BYTES = 2 * 1024 * 1024;
    private static final JsonMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(20)
                            .maxStringLength(8192).maxNameLength(256).maxNumberLength(128).build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                    .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build())
            .findAndAddModules()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    public static final PkbPayload EMPTY = new PkbPayload(MAPPER.createObjectNode());
    private final JsonNode value;
    private final String json;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public PkbPayload(JsonNode value) {
        if (value == null || !value.isObject()) throw invalid();
        this.value = normalize(value, 1, new int[]{0});
        this.json = MAPPER.writeValueAsString(this.value);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new PkbPayloadFailure(413, "payload_too_large");
    }

    public static PkbPayload fromJson(String json) { return new PkbPayload(MAPPER.readTree(json)); }
    public static JsonMapper mapper() { return MAPPER; }
    public static PkbPayload fromMap(java.util.Map<String, Object> value) {
        return new PkbPayload(MAPPER.valueToTree(value));
    }
    /** Validate bytes before a parser can autodetect another encoding or accept noncanonical UTF-8. */
    public static void checkEncoding(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes));
        } catch (java.nio.charset.CharacterCodingException failure) { throw invalid(); }
        // Raw NUL is illegal JSON; also prevents UTF-16/32 autodetection around the UTF-8 contract.
        for (byte value : bytes) if (value == 0) throw invalid();
    }
    public String json() { return json; }
    public boolean isEmpty() { return value.isEmpty(); }
    @JsonValue public JsonNode value() { return value.deepCopy(); }
    @Override public boolean equals(Object other) { return other instanceof PkbPayload data && value.equals(data.value); }
    @Override public int hashCode() { return value.hashCode(); }
    @Override public String toString() { return "PkbPayload[redacted]"; }

    private static JsonNode normalize(JsonNode node, int depth, int[] nodes) {
        if (++nodes[0] > 2048 || depth > 16) throw invalid();
        if (node.isObject()) {
            if (node.size() > 128) throw invalid();
            var result = MAPPER.createObjectNode();
            for (var property : node.properties()) {
                checkString(property.getKey(), 256);
                var child = property.getValue();
                result.set(property.getKey(), normalize(child, depth + ((child.isObject() || child.isArray()) ? 1 : 0), nodes));
            }
            return result;
        }
        if (node.isArray()) {
            if (node.size() > 512) throw invalid();
            var result = MAPPER.createArrayNode();
            for (var child : node) result.add(normalize(child, depth + ((child.isObject() || child.isArray()) ? 1 : 0), nodes));
            return result;
        }
        if (node.isString()) { checkString(node.stringValue(), 8192); return node; }
        if (node.isNumber()) {
            if (node.numberValue() instanceof Double || node.numberValue() instanceof Float) throw invalid();
            BigDecimal number = node.decimalValue();
            // Check raw bounds before normalization; JSONB may expand exponent notation on read.
            if (number.precision() > 128 || number.scale() < -64 || number.scale() > 192) throw invalid();
            number = number.stripTrailingZeros();
            if (number.precision() > 64 || number.scale() < -64 || number.scale() > 64) throw invalid();
            return MAPPER.getNodeFactory().numberNode(number);
        }
        if (node.isNull() || node.isBoolean()) return node;
        throw invalid();
    }

    private static void checkString(String text, int maxBytes) {
        if (text.getBytes(StandardCharsets.UTF_8).length > maxBytes) throw invalid();
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == 0) throw invalid();
            if (Character.isHighSurrogate(character)) {
                if (++i == text.length() || !Character.isLowSurrogate(text.charAt(i))) throw invalid();
            } else if (Character.isLowSurrogate(character)) throw invalid();
        }
    }

    private static PkbPayloadFailure invalid() { return new PkbPayloadFailure(422, "invalid_request"); }
}
