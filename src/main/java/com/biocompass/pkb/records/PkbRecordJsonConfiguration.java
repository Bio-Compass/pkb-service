package com.biocompass.pkb.records;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Scope exact JSON parsing and parser constraints to source-record creation. */
@Configuration
public class PkbRecordJsonConfiguration implements WebMvcConfigurer {
    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConverters(converter -> {
            if (converter instanceof JacksonJsonHttpMessageConverter json) {
                json.registerMappersForType(PkbRecordRequests.Create.class, mappers -> {
                    mappers.put(MediaType.APPLICATION_JSON, PkbExtraData.mapper());
                    mappers.put(MediaType.parseMediaType("application/*+json"), PkbExtraData.mapper());
                });
            }
        });
    }
}
