package com.biocompass.pkb.records;

import com.biocompass.pkb.payload.PkbPayload;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PkbPayloadTest {
    @Test void preservesAllJsonTypesExactDecimalsAndOrderedArrays() {
        var data = PkbPayload.fromJson("""
                {"recipe":{"name":"чай 🍵","enabled":true,"notes":null,"ingredients":["water",2,false]},
                 "precise":0.123456789012345678901234567890123456789,"large":123456789012345678901234567890}
                """);
        assertThat(data.value().path("precise").decimalValue())
                .isEqualByComparingTo(new BigDecimal("0.123456789012345678901234567890123456789"));
        assertThat(data.value().path("recipe").path("notes").isNull()).isTrue();
        assertThat(PkbPayload.fromJson(data.json())).isEqualTo(data);
        assertThat(data.toString()).doesNotContain("recipe", "precise", "чай");
    }

    @Test void equalityIgnoresObjectOrderAndNumericSpellingButPreservesJsonMeaning() {
        var original = PkbPayload.fromJson("{\"b\":[1,2],\"a\":{\"n\":250}}");
        assertThat(PkbPayload.fromJson("{\"a\":{\"n\":250.00},\"b\":[1.0,2e0]}"))
                .isEqualTo(original).hasSameHashCodeAs(original);
        assertThat(PkbPayload.fromJson("{\"b\":[2,1],\"a\":{\"n\":250}}" )).isNotEqualTo(original);
        assertThat(PkbPayload.fromJson("{\"b\":[1,2],\"a\":{\"n\":\"250\"}}" )).isNotEqualTo(original);
        assertThat(PkbPayload.fromJson("{\"b\":[1,2],\"a\":{\"n\":250,\"x\":null}}" )).isNotEqualTo(original);
    }

    @Test void mutableInputAndReturnedTreesCannotChangeStoredData() {
        var tree = PkbPayload.mapper().createObjectNode().putObject("nested");
        tree.put("value", 1);
        var input = PkbPayload.mapper().createObjectNode().set("nested", tree);
        var data = new PkbPayload(input);
        tree.put("value", 2);
        ((tools.jackson.databind.node.ObjectNode) data.value().path("nested")).put("value", 3);
        assertThat(data.value().path("nested").path("value").intValue()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"null", "[]", "1", "true", "\"encoded JSON\"",
            "{\"a\":1,\"a\":2}", "{\"a\":1,\"\\u0061\":2}", "{} {}", "{\"n\":NaN}",
            "{\"n\":1e10000000}", "{\"n\":1e-65}", "{\"nul\":\"\\u0000\"}", "{\"bad\":\"\\ud800\"}"})
    void rejectsInvalidOrAmbiguousJson(String json) {
        assertThatThrownBy(() -> PkbPayload.fromJson(json)).isInstanceOf(RuntimeException.class);
    }

    @Test void boundsDepthAndExactNumericPrecision() {
        assertThat(PkbPayload.fromJson(nested(16))).isNotNull();
        assertThatThrownBy(() -> PkbPayload.fromJson(nested(17))).isInstanceOf(com.biocompass.pkb.payload.PkbPayloadFailure.class);
        assertThat(PkbPayload.fromJson("{\"n\":" + "9".repeat(64) + "}")).isNotNull();
        assertThatThrownBy(() -> PkbPayload.fromJson("{\"n\":" + "9".repeat(65) + "}"))
                .isInstanceOf(com.biocompass.pkb.payload.PkbPayloadFailure.class);
        assertThat(PkbPayload.fromJson("{\"n\":1e64}")).isNotNull();
    }

    @Test void expandedDatabaseNumbersKeepTheirSemanticPrecisionAndScale() {
        for (String number : new String[]{"1e64", "-"+"9".repeat(64)+"e64", "1e-64", "1.0000000000000000"}) {
            var data = PkbPayload.fromJson("{\"n\":"+number+"}");
            assertThat(data.json()).doesNotContain("E+");
            assertThat(PkbPayload.fromJson(data.json())).isEqualTo(data);
        }
    }

    @Test void boundsContainerWidthsAndTotalNodes() {
        assertThat(PkbPayload.fromJson(object(128))).isNotNull();
        assertThatThrownBy(() -> PkbPayload.fromJson(object(129))).isInstanceOf(com.biocompass.pkb.payload.PkbPayloadFailure.class);
        String array = IntStream.range(0,512).mapToObj(i -> "null").collect(Collectors.joining(",", "[", "]"));
        assertThat(PkbPayload.fromJson("{\"a\":"+array+"}")).isNotNull();
        assertThatThrownBy(() -> PkbPayload.fromJson("{\"a\":"+array.replace("]", ",null]")+"}"))
                .isInstanceOf(com.biocompass.pkb.payload.PkbPayloadFailure.class);
        String values = IntStream.range(0,510).mapToObj(i -> "null").collect(Collectors.joining(","));
        String atLimit = "{\"a\":["+values+",null],\"b\":["+values+",null],\"c\":["+values+",null],\"d\":["+values+"]}";
        assertThat(PkbPayload.fromJson(atLimit)).isNotNull();
        assertThatThrownBy(() -> PkbPayload.fromJson(atLimit.replace("\"d\":[", "\"d\":[null,")))
                .isInstanceOf(com.biocompass.pkb.payload.PkbPayloadFailure.class);
    }

    @Test void boundsDecodedUtf8StringsAndCompactObjectSize() {
        assertThat(PkbPayload.fromJson("{\"x\":\""+"é".repeat(4096)+"\"}")).isNotNull();
        assertThatThrownBy(() -> PkbPayload.fromJson("{\"x\":\""+"é".repeat(4097)+"\"}"))
                .isInstanceOf(com.biocompass.pkb.payload.PkbPayloadFailure.class);
        assertThat(PkbPayload.fromJson("{\""+"a".repeat(256)+"\":1}")).isNotNull();
        assertThatThrownBy(() -> PkbPayload.fromJson("{\""+"a".repeat(257)+"\":1}"))
                .isInstanceOf(RuntimeException.class);
        String exact = sizedObject(0);
        assertThat(exact.getBytes(StandardCharsets.UTF_8)).hasSize(PkbPayload.MAX_BYTES);
        assertThat(PkbPayload.fromJson(exact)).isNotNull();
        assertThatThrownBy(() -> PkbPayload.fromJson(sizedObject(1))).isInstanceOf(com.biocompass.pkb.payload.PkbPayloadFailure.class);
    }

    private static String nested(int depth) { return "{\"a\":".repeat(depth) + "null" + "}".repeat(depth); }
    private static String object(int members) {
        return IntStream.range(0,members).mapToObj(i -> "\"k"+i+"\":null").collect(Collectors.joining(",","{","}"));
    }
    private static String sizedObject(int additionalBytes) {
        return IntStream.range(0,8).mapToObj(i -> "\"s"+i+"\":\""+"a".repeat(i==0 ? 8192-65+additionalBytes : 8192)+"\"")
                .collect(Collectors.joining(",","{","}"));
    }
}
