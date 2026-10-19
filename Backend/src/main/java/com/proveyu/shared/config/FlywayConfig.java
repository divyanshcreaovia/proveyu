package com.proveyu.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try {
                log.info("[FLYWAY] Running flyway repair to align migration checksums...");
                flyway.repair();
            } catch (Exception e) {
                log.warn("[FLYWAY] Repair warning: {}", e.getMessage());
            }
            log.info("[FLYWAY] Executing database migrations...");
            flyway.migrate();
        };
    }
}
