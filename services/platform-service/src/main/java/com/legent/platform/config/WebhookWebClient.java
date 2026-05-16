package com.legent.platform.config;

import org.springframework.web.reactive.function.client.WebClient;

public class WebhookWebClient {

    private final WebClient delegate;

    public WebhookWebClient(WebClient delegate) {
        this.delegate = delegate;
    }

    public WebClient.RequestBodyUriSpec post() {
        return delegate.post();
    }
}
