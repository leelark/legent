package com.legent.audience.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.Properties;

@Configuration
public class AudienceKafkaConsumerConfig {

    @Value("${legent.audience.kafka.tracking-ingested.listener.concurrency:${spring.kafka.listener.concurrency:3}}")
    private int trackingIngestedListenerConcurrency;

    @Value("${legent.audience.kafka.tracking-ingested.consumer.max-poll-records:500}")
    private int trackingIngestedMaxPollRecords;

    @Bean(name = "audienceTrackingIngestedKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> audienceTrackingIngestedKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setBatchListener(true);
        factory.setConcurrency(atLeastOne(trackingIngestedListenerConcurrency));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);

        Properties consumerProperties = new Properties();
        consumerProperties.put(
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                String.valueOf(atLeastOne(trackingIngestedMaxPollRecords)));
        factory.getContainerProperties().setKafkaConsumerProperties(consumerProperties);

        return factory;
    }

    private int atLeastOne(int value) {
        return Math.max(1, value);
    }
}
