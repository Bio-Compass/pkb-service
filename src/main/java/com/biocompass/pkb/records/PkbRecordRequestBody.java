package com.biocompass.pkb.records;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Bounded buffering for the synchronous MVC JSON reader, after authority verification. */
final class PkbRecordRequestBody {
    private PkbRecordRequestBody() {}

    static HttpServletRequest bounded(HttpServletRequest request) throws IOException {
        if (!"POST".equals(request.getMethod())) return request;
        if (request.getContentLengthLong() > PkbExtraData.MAX_REQUEST_BYTES) throw tooLarge();
        byte[] bytes = request.getInputStream().readNBytes(PkbExtraData.MAX_REQUEST_BYTES + 1);
        if (bytes.length > PkbExtraData.MAX_REQUEST_BYTES) throw tooLarge();
        return new HttpServletRequestWrapper(request) {
            @Override public int getContentLength() { return bytes.length; }
            @Override public long getContentLengthLong() { return bytes.length; }
            @Override public BufferedReader getReader() throws IOException {
                return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
            @Override public ServletInputStream getInputStream() {
                var source = new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    @Override public int read() { return source.read(); }
                    @Override public int read(byte[] target, int offset, int length) { return source.read(target, offset, length); }
                    @Override public boolean isFinished() { return source.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) {
                        throw new IllegalStateException("Synchronous JSON reader required");
                    }
                };
            }
        };
    }

    private static PkbRecordFailure tooLarge() { return new PkbRecordFailure(413, "payload_too_large"); }
}
