package com.legent.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "com.legent.content",
        "com.legent.common",
        "com.legent.security",
        "com.legent.kafka",
        "com.legent.cache"
})
@EnableCaching
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.legent.content.repository")
public class ContentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentApplication.class, args);
    }
}
