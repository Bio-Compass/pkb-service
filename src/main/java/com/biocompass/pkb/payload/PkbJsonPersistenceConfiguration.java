package com.biocompass.pkb.payload;

import java.lang.reflect.Type;
import org.hibernate.type.format.AbstractJsonFormatMapper;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PkbJsonPersistenceConfiguration {
    @Bean HibernatePropertiesCustomizer exactJsonPersistence() {
        return properties -> properties.put("hibernate.type.json_format_mapper", new AbstractJsonFormatMapper() {
            @Override protected <T> T fromString(CharSequence json, Type type) {
                return PkbPayload.mapper().readValue(json.toString(), PkbPayload.mapper().constructType(type));
            }
            @Override protected <T> String toString(T value, Type type) {
                return PkbPayload.mapper().writerFor(PkbPayload.mapper().constructType(type)).writeValueAsString(value);
            }
        });
    }
}
