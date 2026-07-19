package com.biocompass.pkb.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;

@ConfigurationProperties(prefix = "biocompass.pkb.auth")
public record PkbAuthProperties(
        Introspection introspection,
        String defaultPurpose
) {

    public PkbAuthProperties {
        introspection = introspection == null
                ? new Introspection(null, null, null, null)
                : introspection;
        defaultPurpose = StringUtils.hasText(defaultPurpose) ? defaultPurpose : "self";
    }

    public record Introspection(
            URI url,
            String serviceName,
            String serviceToken,
            Integer timeoutSeconds
    ) {

        public Introspection {
            if (url == null) {
                url = URI.create("http://localhost:8001/api/v1/internal/auth/token/introspect/");
            }
            serviceName = StringUtils.hasText(serviceName) ? serviceName : "pkb-service";
            timeoutSeconds = timeoutSeconds == null || timeoutSeconds <= 0 ? 5 : timeoutSeconds;
        }
    }
}
