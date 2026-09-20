package com.biocompass.pkb.kafka;

import com.biocompass.pkb.authorization.PkbAuthorizationUnavailableException;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

@Configuration(proxyBeanMethods = false)
public class PkbKafkaConfiguration {

    @Bean
    DefaultKafkaProducerFactoryCustomizer pkbKafkaProducerFactoryCustomizer() {
        return PkbKafkaConfiguration::configureValueSerializer;
    }

    @Bean
    CommonErrorHandler pkbKafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
            PkbKafkaProperties properties
    ) {
        var recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(properties.deadLetterTopic(), record.partition()));
        recoverer.setFailIfSendResultIsError(true);

        var retry = properties.retry();
        var backOff = new ExponentialBackOffWithMaxRetries(retry.maxRetries());
        backOff.setInitialInterval(retry.initialInterval().toMillis());
        backOff.setMultiplier(retry.multiplier());
        backOff.setMaxInterval(retry.maxInterval().toMillis());

        var errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.defaultFalse(true);
        errorHandler.addRetryableExceptions(
                PkbAuthorizationUnavailableException.class,
                TransientDataAccessException.class,
                RecoverableDataAccessException.class,
                DataAccessResourceFailureException.class);
        return errorHandler;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void configureValueSerializer(DefaultKafkaProducerFactory producerFactory) {
        producerFactory.setValueSerializerSupplier(() -> new DelegatingByTypeSerializer(Map.of(
                byte[].class, new ByteArraySerializer(),
                Object.class, new JacksonJsonSerializer<>()
        ), true));
    }
}
