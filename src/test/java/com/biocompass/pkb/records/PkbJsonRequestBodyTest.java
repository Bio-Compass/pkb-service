package com.biocompass.pkb.records;

import com.biocompass.pkb.payload.PkbJsonRequestBody;
import com.biocompass.pkb.payload.PkbPayload;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PkbJsonRequestBodyTest {
    @Test void boundsActualBytesWhenContentLengthIsAbsentOrIncorrect() {
        for (long declaredLength : new long[]{-1, 1}) {
            var request = new MockHttpServletRequest("POST", "/api/v1/internal/items") {
                @Override public long getContentLengthLong() { return declaredLength; }
            };
            request.setContent(new byte[PkbPayload.MAX_REQUEST_BYTES + 1]);
            assertThatThrownBy(() -> PkbJsonRequestBody.bounded(request))
                    .isInstanceOfSatisfying(com.biocompass.pkb.payload.PkbPayloadFailure.class, failure -> {
                        assertThat(failure.status()).isEqualTo(413);
                        assertThat(failure.code()).isEqualTo("payload_too_large");
                    });
        }
    }

    @Test void acceptsTheExactLimitAndReplaysTheSameBodyToTheMvcConverter() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/internal/items") {
            @Override public long getContentLengthLong() { return -1; }
        };
        byte[] bytes = " ".repeat(PkbPayload.MAX_REQUEST_BYTES).getBytes(StandardCharsets.UTF_8);
        request.setContent(bytes);
        var wrapped = PkbJsonRequestBody.bounded(request);
        assertThat(wrapped.getContentLengthLong()).isEqualTo(bytes.length);
        assertThat(wrapped.getInputStream().readAllBytes()).isEqualTo(bytes);
        assertThat(wrapped.getReader().readLine()).isEqualTo(new String(bytes,StandardCharsets.UTF_8));
    }

    @Test void rejectsInvalidUtf8AndAlternateEncodingsBeforeJsonParsing() {
        for (byte[] bytes : new byte[][]{new byte[]{'"', (byte)0xc0, (byte)0xaf, '"'},
                new byte[]{'"', (byte)0xed, (byte)0xa0, (byte)0x80, '"'},
                "{\"a\":1}".getBytes(StandardCharsets.UTF_16LE)}) {
            var request = new MockHttpServletRequest("POST", "/api/v1/internal/items");
            request.setContent(bytes);
            assertThatThrownBy(() -> PkbJsonRequestBody.bounded(request))
                    .isInstanceOf(com.biocompass.pkb.payload.PkbPayloadFailure.class);
        }
    }

    @Test void nonBodyReadsKeepTheOriginalRequest() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/internal/items");
        assertThat(PkbJsonRequestBody.bounded(request)).isSameAs(request);
    }
}
