package com.legent.kafka.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import com.legent.common.constant.AppConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.lang.NonNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kafka consumer configuration with JSON deserialization,
 * manual ack mode, and exponential backoff error handling.
 */
@Configuration

public class KafkaConsumerConfig {

    static final int DEFAULT_DLQ_PARTITIONS = 6;
    static final String TRUSTED_PACKAGES_ALLOWLIST = "java.lang,java.util,com.legent.kafka.model";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:legent-default}")
    private String groupId;

    @Value("${spring.kafka.consumer.properties.spring.json.trusted.packages:" + TRUSTED_PACKAGES_ALLOWLIST + "}")
    private String trustedPackages;

    @Value("${spring.kafka.consumer.properties.spring.json.value.default.type:com.legent.kafka.model.EventEnvelope}")
    private String valueDefaultType;

    @Value("${spring.kafka.consumer.properties.spring.json.use.type.headers:false}")
    private boolean useTypeInfoHeaders;

    @Bean
    @NonNull
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // Error-handling deserializer wrapping JSON
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class.getName());
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, validatedTrustedPackages());
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, valueDefaultType);
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, useTypeInfoHeaders);
        
        // Stability settings
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 45000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> dlqDestination(record)
        );

        // Exponential backoff: 1s initial, 2x multiplier, 30s max elapsed.
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxElapsedTime(30000L);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    static TopicPartition dlqDestination(ConsumerRecord<?, ?> record) {
        return new TopicPartition(AppConstants.TOPIC_KAFKA_DLQ, record.partition());
    }

    private String validatedTrustedPackages() {
        Set<String> configuredPackages = Arrays.stream(trustedPackages.split(","))
                .map(String::trim)
                .filter(packageName -> !packageName.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> allowedPackages = Arrays.stream(TRUSTED_PACKAGES_ALLOWLIST.split(","))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!allowedPackages.equals(configuredPackages)) {
            throw new IllegalStateException(
                    "Kafka consumer trusted packages must be exactly " + TRUSTED_PACKAGES_ALLOWLIST);
        }

        return TRUSTED_PACKAGES_ALLOWLIST;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory((ConsumerFactory<? super String, ? super Object>) consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(kafkaErrorHandler);

        return factory;
    }
}
