package com.biocompass.pkb.command.api.web;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.customizers.ParameterCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class PkbCommandWebConfig implements WebMvcConfigurer {

    private final PkbCommandHeaderArgumentResolver commandHeaderArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(commandHeaderArgumentResolver);
    }

    @Bean
    ParameterCustomizer pkbCommandHeaderParameterCustomizer() {
        return (parameter, methodParameter) -> {
            if (methodParameter.hasParameterAnnotation(CorrelationId.class)) {
                parameter.setRequired(false);
            }
            return parameter;
        };
    }
}
