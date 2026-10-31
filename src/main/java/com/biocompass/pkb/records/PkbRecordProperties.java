package com.biocompass.pkb.records;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("biocompass.pkb.records")
public record PkbRecordProperties(Map<String, String> serviceTokens, URI authorityUrl,
        String authorityServiceToken, Duration timeout) {
    public PkbRecordProperties {
        serviceTokens = serviceTokens == null ? Map.of() : Map.copyOf(serviceTokens);
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30)) > 0)
            throw new IllegalArgumentException("Invalid record authority timeout");
        if (authorityUrl != null && (!Set.of("http", "https").contains(authorityUrl.getScheme())
                || authorityUrl.getHost() == null || authorityUrl.getUserInfo() != null))
            throw new IllegalArgumentException("Invalid authority URL");
    }
    @Override public String toString() { return "PkbRecordProperties[redacted]"; }
}
