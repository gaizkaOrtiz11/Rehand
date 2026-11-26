package com.rehand.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Switches on {@code @Scheduled} methods ({@link com.rehand.service.OrderScheduler}, {@link com.rehand.service.OfferService}). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
