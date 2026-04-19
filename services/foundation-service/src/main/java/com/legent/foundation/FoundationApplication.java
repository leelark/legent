package com.legent.foundation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.legent.foundation",
        "com.legent.common",
        "com.legent.security",
        "com.legent.kafka",
        "com.legent.cache"
})
@ConfigurationPropertiesScan
public class FoundationApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoundationApplication.class, args);
    }
}
