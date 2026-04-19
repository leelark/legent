package com.legent.audience;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.legent.audience",
        "com.legent.common",
        "com.legent.security",
        "com.legent.kafka",
        "com.legent.cache"
})
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
public class AudienceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AudienceApplication.class, args);
    }
}
