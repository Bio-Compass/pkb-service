package com.biocompass.pkb.auth;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("biocompass.pkb.auth.workload")
public record PkbWorkloadAuthenticationProperties(Map<String, String> serviceTokens) {
    public PkbWorkloadAuthenticationProperties {
        serviceTokens = serviceTokens == null ? Map.of() : Map.copyOf(serviceTokens);
    }

    @Override public String toString() { return "PkbWorkloadAuthenticationProperties[redacted]"; }
}
