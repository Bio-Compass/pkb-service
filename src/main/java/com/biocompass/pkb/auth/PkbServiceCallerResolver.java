package com.biocompass.pkb.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PkbServiceCallerResolver {
    public String resolve() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute(PkbLegacyServiceFilter.CALLER_ATTRIBUTE) instanceof String service) {
            return service;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated PKB service caller");
    }
}
