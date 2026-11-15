package com.biocompass.pkb.records;

import com.biocompass.pkb.payload.PkbPayload;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Scope exact JSON parsing and parser constraints to both fact creation transports. */
@Configuration
public class PkbRecordJsonConfiguration implements WebMvcConfigurer {
    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConverters(converter -> {
            if (converter instanceof JacksonJsonHttpMessageConverter json) {
                for (var type : java.util.List.of(PkbRecordRequests.Create.class, PkbRecordController.Item.class,
                        PkbRecordController.Page.class, PkbRecordController.WriteResponse.class,
                        com.biocompass.pkb.command.api.model.CreatePkbItemRequest.class,
                        com.biocompass.pkb.command.api.model.SupersedePkbItemRequest.class)) {
                    json.registerMappersForType(type, mappers -> {
                        mappers.put(MediaType.APPLICATION_JSON, PkbPayload.mapper());
                        mappers.put(MediaType.parseMediaType("application/*+json"), PkbPayload.mapper());
                    });
                }
            }
        });
    }
}
