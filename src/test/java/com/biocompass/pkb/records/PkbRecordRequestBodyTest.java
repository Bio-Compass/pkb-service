package com.biocompass.pkb.records;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PkbRecordRequestBodyTest {
    @Test void boundsActualBytesWhenContentLengthIsAbsentOrIncorrect() {
        for (long declaredLength : new long[]{-1, 1}) {
            var request = new MockHttpServletRequest("POST", "/api/v1/internal/items") {
                @Override public long getContentLengthLong() { return declaredLength; }
            };
            request.setContent(new byte[PkbExtraData.MAX_REQUEST_BYTES + 1]);
            assertThatThrownBy(() -> PkbRecordRequestBody.bounded(request))
                    .isInstanceOfSatisfying(PkbRecordFailure.class, failure -> {
                        assertThat(failure.status()).isEqualTo(413);
                        assertThat(failure.code()).isEqualTo("payload_too_large");
                    });
        }
    }

    @Test void acceptsTheExactLimitAndReplaysTheSameBodyToTheMvcConverter() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/internal/items") {
            @Override public long getContentLengthLong() { return -1; }
        };
        byte[] bytes = " ".repeat(PkbExtraData.MAX_REQUEST_BYTES).getBytes(StandardCharsets.UTF_8);
        request.setContent(bytes);
        var wrapped = PkbRecordRequestBody.bounded(request);
        assertThat(wrapped.getContentLengthLong()).isEqualTo(bytes.length);
        assertThat(wrapped.getInputStream().readAllBytes()).isEqualTo(bytes);
        assertThat(wrapped.getReader().readLine()).isEqualTo(new String(bytes,StandardCharsets.UTF_8));
    }

    @Test void nonBodyReadsKeepTheOriginalRequest() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/internal/items");
        assertThat(PkbRecordRequestBody.bounded(request)).isSameAs(request);
    }
}
