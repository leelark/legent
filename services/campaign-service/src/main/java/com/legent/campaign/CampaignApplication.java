package com.legent.campaign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.legent.campaign", "com.legent.security", "com.legent.cache", "com.legent.kafka", "com.legent.common"})
@EnableAsync
@EnableScheduling
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.legent.campaign.repository")
public class CampaignApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampaignApplication.class, args);
    }
}
