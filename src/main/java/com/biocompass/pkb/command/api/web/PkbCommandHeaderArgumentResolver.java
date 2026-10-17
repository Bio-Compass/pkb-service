package com.biocompass.pkb.command.api.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PkbCommandHeaderArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String COMMAND_ID_HEADER = "X-Command-Id";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (parameter.hasParameterAnnotation(CommandId.class)) {
            return UUID.class.isAssignableFrom(parameter.getParameterType());
        }
        return parameter.hasParameterAnnotation(CorrelationId.class)
                && String.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            @NonNull MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Current request is unavailable.");
        }
        if (parameter.hasParameterAnnotation(CommandId.class)) {
            return resolveCommandId(request);
        }
        return resolveCorrelationId(request);
    }

    private UUID resolveCommandId(HttpServletRequest request) {
        String value = request.getHeader(COMMAND_ID_HEADER);
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required request header: " + COMMAND_ID_HEADER);
        }
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, COMMAND_ID_HEADER + " must be a UUID.", exception);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String value = request.getHeader(CORRELATION_ID_HEADER);
        return StringUtils.hasText(value) ? value.strip() : resolveCommandId(request).toString();
    }
}
