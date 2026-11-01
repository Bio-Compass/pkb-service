package com.biocompass.pkb.auth;

import com.biocompass.pkb.authorization.PkbAuthorizationProperties;
import com.biocompass.pkb.records.PkbRecordProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates the workload before the legacy user bearer-token filter runs. */
public final class PkbLegacyServiceFilter extends OncePerRequestFilter {
    public static final String SERVICE_HEADER = "X-BioCompass-Service";
    public static final String TOKEN_HEADER = "X-BioCompass-Service-Token";
    static final String CALLER_ATTRIBUTE = PkbLegacyServiceFilter.class.getName() + ".caller";
    private final Map<String, String> serviceTokens;

    public PkbLegacyServiceFilter(PkbLegacyServiceProperties properties, PkbRecordProperties records,
            PkbAuthProperties auth, PkbAuthorizationProperties authorization) {
        serviceTokens = properties.serviceTokens();
        // Validate after binding so configuration failures never render raw credential values.
        var credentials = new HashSet<String>();
        serviceTokens.forEach((name, token) -> {
            if (!StringUtils.hasText(name) || !name.equals(name.strip()) || !StringUtils.hasText(token)
                    || !token.equals(token.strip()) || !credentials.add(token)) {
                throw new IllegalArgumentException("Legacy PKB callers require explicit, distinct credentials");
            }
        });
        var isolated = new HashSet<>(records.serviceTokens().values());
        isolated.add(records.authorityServiceToken());
        isolated.add(auth.introspection().serviceToken());
        isolated.add(authorization.serviceToken());
        var normalizedIsolated = isolated.stream().filter(StringUtils::hasText).map(String::strip).toList();
        if (serviceTokens.values().stream().anyMatch(normalizedIsolated::contains)) {
            throw new IllegalArgumentException("Legacy PKB credentials must be isolated from other capabilities");
        }
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        var service = singleHeader(request, SERVICE_HEADER);
        var token = singleHeader(request, TOKEN_HEADER);
        var expected = service == null ? null : serviceTokens.get(service);
        if (!StringUtils.hasText(expected) || token == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"unauthorized_service\",\"detail\":\"Service authentication required.\"}");
            return;
        }
        request.setAttribute(CALLER_ATTRIBUTE, service);
        try { chain.doFilter(request, response); }
        finally { request.removeAttribute(CALLER_ATTRIBUTE); }
    }

    private static String singleHeader(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        return values.size() == 1 ? values.getFirst() : null;
    }
}
