package com.legent.tracking.config;

import com.legent.common.constant.AppConstants;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Properties;

@Configuration
public class TrackingKafkaConsumerConfig {

    private static final Duration DEFAULT_STALE_CLAIM_AGE = Duration.ofMinutes(15);
    private static final Duration DEFAULT_RETRY_INITIAL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration DEFAULT_RETRY_MAX_INTERVAL = Duration.ofSeconds(30);
    private static final Duration DEFAULT_RETRY_BUFFER = Duration.ofMinutes(1);
    private static final Duration DEFAULT_MAX_POLL_INTERVAL = Duration.ofMinutes(20);

    @Value("${legent.tracking.kafka.ingested.listener.concurrency:${spring.kafka.listener.concurrency:3}}")
    private int trackingIngestedListenerConcurrency;

    @Value("${legent.tracking.kafka.ingested.consumer.max-poll-records:500}")
    private int trackingIngestedMaxPollRecords;

    @Value("${legent.tracking.kafka.ingested.consumer.max-poll-interval:PT20M}")
    private String trackingIngestedMaxPollInterval = DEFAULT_MAX_POLL_INTERVAL.toString();

    @Value("${legent.tracking.kafka.ingested.retry.initial-interval:PT1S}")
    private String trackingIngestedRetryInitialInterval = DEFAULT_RETRY_INITIAL_INTERVAL.toString();

    @Value("${legent.tracking.kafka.ingested.retry.multiplier:2.0}")
    private double trackingIngestedRetryMultiplier = 2.0;

    @Value("${legent.tracking.kafka.ingested.retry.max-interval:PT30S}")
    private String trackingIngestedRetryMaxInterval = DEFAULT_RETRY_MAX_INTERVAL.toString();

    @Value("${legent.tracking.kafka.ingested.retry.buffer:PT1M}")
    private String trackingIngestedRetryBuffer = DEFAULT_RETRY_BUFFER.toString();

    @Value("${legent.tracking.idempotency.stale-claim-age:PT15M}")
    private String trackingIdempotencyStaleClaimAge = DEFAULT_STALE_CLAIM_AGE.toString();

    @Bean(name = "trackingIngestedKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> trackingIngestedKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(trackingIngestedKafkaErrorHandler(kafkaTemplate));
        factory.setBatchListener(true);
        factory.setConcurrency(atLeastOne(trackingIngestedListenerConcurrency));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);

        Properties consumerProperties = new Properties();
        consumerProperties.put(
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                String.valueOf(atLeastOne(trackingIngestedMaxPollRecords)));
        consumerProperties.put(
                ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,
                String.valueOf(toPositiveMillis(effectiveMaxPollInterval(), DEFAULT_MAX_POLL_INTERVAL.toMillis())));
        factory.getContainerProperties().setKafkaConsumerProperties(consumerProperties);

        return factory;
    }

    DefaultErrorHandler trackingIngestedKafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(AppConstants.TOPIC_KAFKA_DLQ, record.partition())
        );
        ExponentialBackOff backOff = new ExponentialBackOff(
                toPositiveMillis(configuredDuration(
                                trackingIngestedRetryInitialInterval,
                                DEFAULT_RETRY_INITIAL_INTERVAL,
                                "legent.tracking.kafka.ingested.retry.initial-interval"),
                        DEFAULT_RETRY_INITIAL_INTERVAL.toMillis()),
                Math.max(1.0, trackingIngestedRetryMultiplier));
        backOff.setMaxInterval(toPositiveMillis(
                effectiveRetryMaxInterval(),
                DEFAULT_RETRY_MAX_INTERVAL.toMillis()));
        backOff.setMaxElapsedTime(toPositiveMillis(
                effectiveRetryMaxElapsedTime(),
                DEFAULT_STALE_CLAIM_AGE.plus(DEFAULT_RETRY_BUFFER).toMillis()));
        return new DefaultErrorHandler(recoverer, backOff);
    }

    Duration effectiveRetryMaxElapsedTime() {
        Duration staleClaimAge = configuredDuration(
                trackingIdempotencyStaleClaimAge,
                DEFAULT_STALE_CLAIM_AGE,
                "legent.tracking.idempotency.stale-claim-age");
        Duration maxInterval = effectiveRetryMaxInterval();
        Duration maxElapsed = staleClaimAge.plus(configuredDuration(
                trackingIngestedRetryBuffer,
                DEFAULT_RETRY_BUFFER,
                "legent.tracking.kafka.ingested.retry.buffer"));
        if (maxElapsed.compareTo(staleClaimAge.plus(maxInterval)) <= 0) {
            throw new IllegalStateException(
                    "legent.tracking.kafka.ingested.retry.buffer must exceed retry max-interval so stale tracking claims can be retried before DLQ recovery");
        }
        return maxElapsed;
    }

    Duration effectiveRetryMaxInterval() {
        return configuredDuration(
                trackingIngestedRetryMaxInterval,
                DEFAULT_RETRY_MAX_INTERVAL,
                "legent.tracking.kafka.ingested.retry.max-interval");
    }

    Duration effectiveMaxPollInterval() {
        Duration retryWindow = effectiveRetryMaxElapsedTime()
                .plus(effectiveRetryMaxInterval());
        Duration configuredMaxPollInterval = configuredDuration(
                trackingIngestedMaxPollInterval,
                DEFAULT_MAX_POLL_INTERVAL,
                "legent.tracking.kafka.ingested.consumer.max-poll-interval");
        return configuredMaxPollInterval.compareTo(retryWindow) >= 0
                ? configuredMaxPollInterval
                : retryWindow;
    }

    private int atLeastOne(int value) {
        return Math.max(1, value);
    }

    private Duration positiveOrDefault(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }

    private Duration configuredDuration(String value, Duration fallback, String propertyName) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return positiveOrDefault(Duration.parse(value.trim()), fallback);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException(propertyName + " must be an ISO-8601 duration", e);
        }
    }

    private long toPositiveMillis(Duration duration, long fallbackMillis) {
        Duration effectiveDuration = positiveOrDefault(duration, Duration.ofMillis(fallbackMillis));
        return Math.max(1L, effectiveDuration.toMillis());
    }
}
