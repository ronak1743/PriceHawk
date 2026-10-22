package com.pricehawk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class DatabaseSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("Running database schema migration / safety checks...");
        try {
            // PostgreSQL column additions if missing (safe on tables with existing rows)
            jdbcTemplate.execute("ALTER TABLE tracked_items ADD COLUMN IF NOT EXISTS last_notified_price NUMERIC(12, 2)");
            jdbcTemplate.execute("ALTER TABLE tracked_items ADD COLUMN IF NOT EXISTS previous_price NUMERIC(12, 2)");
            jdbcTemplate.execute("ALTER TABLE tracked_items ADD COLUMN IF NOT EXISTS notify_on_any_drop BOOLEAN DEFAULT TRUE");
            
            // Backfill any NULL notify_on_any_drop to true
            jdbcTemplate.execute("UPDATE tracked_items SET notify_on_any_drop = TRUE WHERE notify_on_any_drop IS NULL");

            jdbcTemplate.execute("ALTER TABLE price_alerts ADD COLUMN IF NOT EXISTS read_at TIMESTAMP");

            log.info("Database schema safety checks completed successfully.");
        } catch (Exception e) {
            log.warn("Database schema initialization note: {}", e.getMessage());
        }

        try {
            // Expand text columns to prevent truncation errors on long URLs / titles
            jdbcTemplate.execute("ALTER TABLE tracked_items ALTER COLUMN title TYPE TEXT");
            jdbcTemplate.execute("ALTER TABLE item_sources ALTER COLUMN url TYPE TEXT");
            jdbcTemplate.execute("ALTER TABLE item_sources ALTER COLUMN image_url TYPE TEXT");
            log.info("Text column expansion completed successfully.");
        } catch (Exception e) {
            log.debug("Column type alter note (may be non-PostgreSQL / H2): {}", e.getMessage());
        }
    }
}
