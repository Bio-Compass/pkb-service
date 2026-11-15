package com.biocompass.pkb.records;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PkbExtraDataTest {
    @Test void preservesAllJsonTypesExactDecimalsAndOrderedArrays() {
        var data = PkbExtraData.fromJson("""
                {"recipe":{"name":"чай 🍵","enabled":true,"notes":null,"ingredients":["water",2,false]},
                 "precise":0.123456789012345678901234567890123456789,"large":123456789012345678901234567890}
                """);
        assertThat(data.value().path("precise").decimalValue())
                .isEqualByComparingTo(new BigDecimal("0.123456789012345678901234567890123456789"));
        assertThat(data.value().path("recipe").path("notes").isNull()).isTrue();
        assertThat(PkbExtraData.fromJson(data.json())).isEqualTo(data);
        assertThat(data.toString()).doesNotContain("recipe", "precise", "чай");
    }

    @Test void equalityIgnoresObjectOrderAndNumericSpellingButPreservesJsonMeaning() {
        var original = PkbExtraData.fromJson("{\"b\":[1,2],\"a\":{\"n\":250}}");
        assertThat(PkbExtraData.fromJson("{\"a\":{\"n\":250.00},\"b\":[1.0,2e0]}"))
                .isEqualTo(original).hasSameHashCodeAs(original);
        assertThat(PkbExtraData.fromJson("{\"b\":[2,1],\"a\":{\"n\":250}}" )).isNotEqualTo(original);
        assertThat(PkbExtraData.fromJson("{\"b\":[1,2],\"a\":{\"n\":\"250\"}}" )).isNotEqualTo(original);
        assertThat(PkbExtraData.fromJson("{\"b\":[1,2],\"a\":{\"n\":250,\"x\":null}}" )).isNotEqualTo(original);
    }

    @Test void mutableInputAndReturnedTreesCannotChangeStoredData() {
        var tree = PkbExtraData.mapper().createObjectNode().putObject("nested");
        tree.put("value", 1);
        var input = PkbExtraData.mapper().createObjectNode().set("nested", tree);
        var data = new PkbExtraData(input);
        tree.put("value", 2);
        ((tools.jackson.databind.node.ObjectNode) data.value().path("nested")).put("value", 3);
        assertThat(data.value().path("nested").path("value").intValue()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"null", "[]", "1", "true", "\"encoded JSON\"",
            "{\"a\":1,\"a\":2}", "{\"a\":1,\"\\u0061\":2}", "{} {}", "{\"n\":NaN}",
            "{\"n\":1e10000000}", "{\"n\":1e-65}", "{\"nul\":\"\\u0000\"}", "{\"bad\":\"\\ud800\"}"})
    void rejectsInvalidOrAmbiguousJson(String json) {
        assertThatThrownBy(() -> PkbExtraData.fromJson(json)).isInstanceOf(RuntimeException.class);
    }

    @Test void boundsDepthAndExactNumericPrecision() {
        assertThat(PkbExtraData.fromJson(nested(16))).isNotNull();
        assertThatThrownBy(() -> PkbExtraData.fromJson(nested(17))).isInstanceOf(PkbRecordFailure.class);
        assertThat(PkbExtraData.fromJson("{\"n\":" + "9".repeat(64) + "}")).isNotNull();
        assertThatThrownBy(() -> PkbExtraData.fromJson("{\"n\":" + "9".repeat(65) + "}"))
                .isInstanceOf(PkbRecordFailure.class);
        assertThat(PkbExtraData.fromJson("{\"n\":1e64}")).isNotNull();
    }

    @Test void expandedDatabaseNumbersKeepTheirSemanticPrecisionAndScale() {
        for (String number : new String[]{"1e64", "-"+"9".repeat(64)+"e64", "1e-64", "1.0000000000000000"}) {
            var data = PkbExtraData.fromJson("{\"n\":"+number+"}");
            assertThat(data.json()).doesNotContain("E+");
            assertThat(PkbExtraData.fromJson(data.json())).isEqualTo(data);
        }
    }

    @Test void boundsContainerWidthsAndTotalNodes() {
        assertThat(PkbExtraData.fromJson(object(128))).isNotNull();
        assertThatThrownBy(() -> PkbExtraData.fromJson(object(129))).isInstanceOf(PkbRecordFailure.class);
        String array = IntStream.range(0,512).mapToObj(i -> "null").collect(Collectors.joining(",", "[", "]"));
        assertThat(PkbExtraData.fromJson("{\"a\":"+array+"}")).isNotNull();
        assertThatThrownBy(() -> PkbExtraData.fromJson("{\"a\":"+array.replace("]", ",null]")+"}"))
                .isInstanceOf(PkbRecordFailure.class);
        String values = IntStream.range(0,510).mapToObj(i -> "null").collect(Collectors.joining(","));
        String atLimit = "{\"a\":["+values+",null],\"b\":["+values+",null],\"c\":["+values+",null],\"d\":["+values+"]}";
        assertThat(PkbExtraData.fromJson(atLimit)).isNotNull();
        assertThatThrownBy(() -> PkbExtraData.fromJson(atLimit.replace("\"d\":[", "\"d\":[null,")))
                .isInstanceOf(PkbRecordFailure.class);
    }

    @Test void boundsDecodedUtf8StringsAndCompactObjectSize() {
        assertThat(PkbExtraData.fromJson("{\"x\":\""+"é".repeat(4096)+"\"}")).isNotNull();
        assertThatThrownBy(() -> PkbExtraData.fromJson("{\"x\":\""+"é".repeat(4097)+"\"}"))
                .isInstanceOf(PkbRecordFailure.class);
        assertThat(PkbExtraData.fromJson("{\""+"a".repeat(256)+"\":1}")).isNotNull();
        assertThatThrownBy(() -> PkbExtraData.fromJson("{\""+"a".repeat(257)+"\":1}"))
                .isInstanceOf(RuntimeException.class);
        String exact = sizedObject(0);
        assertThat(exact.getBytes(StandardCharsets.UTF_8)).hasSize(PkbExtraData.MAX_BYTES);
        assertThat(PkbExtraData.fromJson(exact)).isNotNull();
        assertThatThrownBy(() -> PkbExtraData.fromJson(sizedObject(1))).isInstanceOf(PkbRecordFailure.class);
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
