package com.biocompass.pkb.eventing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PkbEnrichmentConsumerConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerConfiguration.class)
            .withPropertyValues("biocompass.pkb.eventing.enabled=true");

    @Test
    void doesNotStartAnEnrichmentConsumerWithoutAnEnrichmentWorkflow() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(PkbIdempotentEventConsumer.class);
            assertThat(context).doesNotHaveBean(PkbKafkaEventConsumer.class);
        });
    }

    @Test
    void startsTheConsumerWhenAnEnrichmentWorkflowIsInstalled() {
        contextRunner
                .withBean(PkbEnrichmentWorkflow.class, () -> event -> {
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(PkbIdempotentEventConsumer.class);
                    assertThat(context).hasSingleBean(PkbKafkaEventConsumer.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({PkbIdempotentEventConsumer.class, PkbKafkaEventConsumer.class})
    static class ConsumerConfiguration {

        @Bean
        PkbProcessedEventDao processedEventDao() {
            return mock(PkbProcessedEventDao.class);
        }

        @Bean
        PkbEventingProperties eventingProperties() {
            return new PkbEventingProperties(false, Duration.ofSeconds(1), 50, "pkb-enrichment", 3, Duration.ofSeconds(5));
        }
    }
}
