package com.legent.foundation.config;

import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "foundation.search.enabled", havingValue = "true")
public class OpenSearchConfig {

    @Value("${opensearch.url}")
    private String openSearchUrl;

    @Bean
    public OpenSearchClient openSearchClient() {
        HttpHost host = HttpHost.create(openSearchUrl);
        RestClient restClient = RestClient.builder(host).build();
        OpenSearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());
        return new OpenSearchClient(transport);
    }
}
