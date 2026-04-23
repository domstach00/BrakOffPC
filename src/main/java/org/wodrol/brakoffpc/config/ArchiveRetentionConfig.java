package org.wodrol.brakoffpc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wodrol.brakoffpc.delivery.DeliveryService;

@Configuration
public class ArchiveRetentionConfig {

    private static final Logger log = LoggerFactory.getLogger(ArchiveRetentionConfig.class);

    @Bean
    ApplicationRunner purgeArchivedDeliveriesRunner(DeliveryService deliveryService) {
        return args -> {
            int deleted = deliveryService.purgeArchivedDeliveriesOlderThanTwoMonths();
            if (deleted > 0) {
                log.info("Usunięto z archiwum stare dostawy liczba={}", deleted);
            }
        };
    }
}
