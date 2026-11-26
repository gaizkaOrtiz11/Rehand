package com.rehand.config;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/**
 * Loads {@code demo-listings.sql} (about 24 demo listings with photos) and {@code demo-orders.sql}
 * (five demo orders, one per status, with their wallet ledger rows) and {@code demo-offers.sql}
 * (open, countered, declined and expired demo offers) after the application has
 * started and {@code data.sql} has created the demo users. Only active when the property
 * {@code app.load-demo-data=true} is set (or the environment variable {@code APP_LOAD_DEMO_DATA=true}).
 * The scripts are idempotent (INSERT IGNORE with fixed ids), so running them on every start is harmless.
 */
@Component
@ConditionalOnProperty(name = "app.load-demo-data", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private final DataSource dataSource;

    public DemoDataLoader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        // demo-orders.sql needs the listings and demo-offers.sql needs both, so they run in this order.
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("demo-listings.sql"),
                new ClassPathResource("demo-orders.sql"),
                new ClassPathResource("demo-offers.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);
    }
}
