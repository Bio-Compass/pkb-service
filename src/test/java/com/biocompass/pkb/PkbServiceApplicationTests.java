package com.biocompass.pkb;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class PkbServiceApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void healthEndpointReportsUp() {
        assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
    }
}
