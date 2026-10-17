package com.biocompass.pkb.authorization;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "biocompass.pkb.authorization")
public record PkbAuthorizationProperties(
        URI decisionUrl,
        String serviceName,
        String serviceToken,
        Duration timeout
) {

    public PkbAuthorizationProperties {
        serviceName = StringUtils.hasText(serviceName) ? serviceName.strip() : "pkb-service";
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
    }
}
