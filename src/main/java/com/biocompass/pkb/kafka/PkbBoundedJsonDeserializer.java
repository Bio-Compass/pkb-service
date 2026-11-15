package com.biocompass.pkb.kafka;

import com.biocompass.pkb.payload.PkbPayload;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

/** ErrorHandlingDeserializer retains the original bytes for permanent failure/DLT recovery. */
public final class PkbBoundedJsonDeserializer extends JacksonJsonDeserializer<Object> {
    public PkbBoundedJsonDeserializer() { super(PkbPayload.mapper()); }
    private static void check(byte[] data) {
        if (data != null && data.length > PkbPayload.MAX_COMMAND_BYTES) {
            throw new SerializationException("PKB JSON message exceeds admission limit");
        }
        if (data != null) PkbPayload.checkEncoding(data);
    }
    @Override public Object deserialize(String topic, Headers headers, byte[] data) {
        check(data);
        try { return super.deserialize(topic, headers, data); }
        catch (RuntimeException failure) { throw new SerializationException("Invalid PKB JSON message"); }
    }
    @Override public Object deserialize(String topic, byte[] data) {
        check(data);
        try { return super.deserialize(topic, data); }
        catch (RuntimeException failure) { throw new SerializationException("Invalid PKB JSON message"); }
    }
}
