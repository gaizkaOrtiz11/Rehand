package com.rehand.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The "Scheduler" actor of the use case diagram: auto-completes delivered orders. Runs every
 * {@code app.orders.auto-complete-check-ms} milliseconds (default 300000, five minutes), the first time
 * one minute after start-up. Scheduling is switched on in {@link com.rehand.config.SchedulingConfig}.
 * The expiry of stale offers is scheduled in {@link OfferService#expireStaleOffers()}.
 */
@Component
public class OrderScheduler {

    private final OrderService orderService;

    public OrderScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${app.orders.auto-complete-check-ms:300000}",
               initialDelayString = "${app.orders.auto-complete-initial-delay-ms:60000}")
    public void autoCompleteDeliveredOrders() {
        orderService.autoCompleteDelivered();
    }
}
