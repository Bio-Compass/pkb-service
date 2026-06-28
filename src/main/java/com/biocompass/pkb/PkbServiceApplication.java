package com.biocompass.pkb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class PkbServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PkbServiceApplication.class, args);
    }
}
