package com.legent.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "legent.webhooks.outbound")
public class WebhookOutboundProperties {

    private String proxyUrl;
    private boolean requireProxyInProduction = true;

    public String getProxyUrl() {
        return proxyUrl;
    }

    public void setProxyUrl(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }

    public boolean isRequireProxyInProduction() {
        return requireProxyInProduction;
    }

    public void setRequireProxyInProduction(boolean requireProxyInProduction) {
        this.requireProxyInProduction = requireProxyInProduction;
    }

    public boolean hasProxyUrl() {
        return proxyUrl != null && !proxyUrl.isBlank();
    }
}
