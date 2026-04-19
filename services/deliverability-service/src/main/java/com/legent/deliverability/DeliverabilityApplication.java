package com.legent.deliverability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"com.legent.deliverability", "com.legent.security", "com.legent.kafka", "com.legent.common"})
@EnableAsync
public class DeliverabilityApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliverabilityApplication.class, args);
    }
}
