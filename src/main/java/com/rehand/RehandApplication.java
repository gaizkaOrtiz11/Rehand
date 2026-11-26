package com.rehand;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. {@code @ConfigurationPropertiesScan} registers
 * {@link com.rehand.config.AppProperties}; {@code @EnableScheduling} is for the
 * offer-expiry and order auto-complete jobs added in later steps.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class RehandApplication {

    public static void main(String[] args) {
        SpringApplication.run(RehandApplication.class, args);
    }
}
