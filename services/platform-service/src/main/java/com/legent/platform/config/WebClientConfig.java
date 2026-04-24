package com.legent.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(5))
                .compress(true);

        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
