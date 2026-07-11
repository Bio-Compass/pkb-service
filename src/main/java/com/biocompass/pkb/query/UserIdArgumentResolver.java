package com.biocompass.pkb.query;

import com.biocompass.pkb.security.BioCompassActorAuthenticationResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class UserIdArgumentResolver implements HandlerMethodArgumentResolver {

    private final BioCompassActorAuthenticationResolver actorResolver;
    private final PkbQueryPolicy queryPolicy;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(UserId.class)
                && UUID.class.isAssignableFrom(parameter.getParameterType());
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

        UUID userId = resolveUserId(parameter, request);
        try {
            queryPolicy.authorizeUserScope(actorResolver.resolve(), userId);
        } catch (PkbAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
        return userId;
    }

    private UUID resolveUserId(MethodParameter parameter, HttpServletRequest request) {
        UserId annotation = parameter.getParameterAnnotation(UserId.class);
        String parameterName = annotation == null ? "userId" : annotation.value();
        String value = request.getParameter(parameterName);
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required request parameter: " + parameterName);
        }
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, parameterName + " must be a UUID.", exception);
        }
    }
}
