package com.biocompass.pkb.payload;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Bounded buffering for the MVC JSON reader, after authority verification. */
public final class PkbJsonRequestBody {
    private PkbJsonRequestBody() {}

    public static HttpServletRequest bounded(HttpServletRequest request) throws IOException {
        if (!"POST".equals(request.getMethod())) return request;
        if (request.getHeader("Content-Encoding") != null) {
            throw new PkbPayloadFailure(415, "unsupported_media_type");
        }
        if (request.getContentLengthLong() > PkbPayload.MAX_REQUEST_BYTES) throw tooLarge();
        byte[] bytes = request.getInputStream().readNBytes(PkbPayload.MAX_REQUEST_BYTES + 1);
        if (bytes.length > PkbPayload.MAX_REQUEST_BYTES) throw tooLarge();
        PkbPayload.checkEncoding(bytes);
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

    private static PkbPayloadFailure tooLarge() { return new PkbPayloadFailure(413, "payload_too_large"); }
}
