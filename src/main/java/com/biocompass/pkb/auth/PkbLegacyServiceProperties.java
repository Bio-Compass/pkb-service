package com.biocompass.pkb.auth;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("biocompass.pkb.auth.legacy")
public record PkbLegacyServiceProperties(Map<String, String> serviceTokens) {
    public PkbLegacyServiceProperties {
        serviceTokens = serviceTokens == null ? Map.of() : Map.copyOf(serviceTokens);
    }

    @Override public String toString() { return "PkbLegacyServiceProperties[redacted]"; }
}
