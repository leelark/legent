package com.legent.tracking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"com.legent.tracking", "com.legent.security", "com.legent.kafka", "com.legent.cache", "com.legent.common"})
@EnableAsync
@org.springframework.scheduling.annotation.EnableScheduling
public class TrackingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackingApplication.class, args);
    }
}
