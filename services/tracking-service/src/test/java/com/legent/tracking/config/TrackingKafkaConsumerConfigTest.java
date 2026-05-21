package com.legent.tracking.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TrackingKafkaConsumerConfigTest {

    @Test
    void trackingIngestedFactoryUsesBatchListenerBatchAckConcurrencyAndBoundedPollRecords() {
        TrackingKafkaConsumerConfig config = new TrackingKafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "trackingIngestedListenerConcurrency", 4);
        ReflectionTestUtils.setField(config, "trackingIngestedMaxPollRecords", 250);
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, Object> consumerFactory = mock(ConsumerFactory.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                config.trackingIngestedKafkaListenerContainerFactory(consumerFactory, kafkaTemplate);

        assertEquals(consumerFactory, factory.getConsumerFactory());
        assertEquals(Boolean.TRUE, factory.isBatchListener());
        assertEquals(ContainerProperties.AckMode.BATCH, factory.getContainerProperties().getAckMode());
        assertEquals(4, new DirectFieldAccessor(factory).getPropertyValue("concurrency"));
        assertEquals(
                "250",
                factory.getContainerProperties()
                        .getKafkaConsumerProperties()
                        .getProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG));
        assertEquals(
                String.valueOf(Duration.ofMinutes(20).toMillis()),
                factory.getContainerProperties()
                        .getKafkaConsumerProperties()
                        .getProperty(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG));
        assertTrue(new DirectFieldAccessor(factory).getPropertyValue("commonErrorHandler") instanceof DefaultErrorHandler);
    }

    @Test
    void trackingIngestedFactoryFloorsInvalidConcurrencyAndPollRecordsAtOne() {
        TrackingKafkaConsumerConfig config = new TrackingKafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "trackingIngestedListenerConcurrency", 0);
        ReflectionTestUtils.setField(config, "trackingIngestedMaxPollRecords", -25);
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, Object> consumerFactory = mock(ConsumerFactory.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                config.trackingIngestedKafkaListenerContainerFactory(consumerFactory, kafkaTemplate);

        assertEquals(1, new DirectFieldAccessor(factory).getPropertyValue("concurrency"));
        assertEquals(
                "1",
                factory.getContainerProperties()
                        .getKafkaConsumerProperties()
                        .getProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG));
        assertTrue(String.valueOf(factory.getContainerProperties().getKafkaConsumerProperties())
                .contains(ConsumerConfig.MAX_POLL_RECORDS_CONFIG));
    }

    @Test
    void trackingRetryWindowExtendsBeyondIdempotencyStaleClaimAgeAndMaxPollCoversRetryWindow() {
        TrackingKafkaConsumerConfig config = new TrackingKafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "trackingIdempotencyStaleClaimAge", "PT15M");
        ReflectionTestUtils.setField(config, "trackingIngestedRetryBuffer", "PT2M");
        ReflectionTestUtils.setField(config, "trackingIngestedMaxPollInterval", "PT10M");

        assertEquals(Duration.ofMinutes(17), config.effectiveRetryMaxElapsedTime());
        assertEquals(Duration.ofMinutes(17).plusSeconds(30), config.effectiveMaxPollInterval());
    }

    @Test
    void trackingRetryWindowFailsFastWhenItCannotOutlastStaleClaimAndOneMoreInterval() {
        TrackingKafkaConsumerConfig config = new TrackingKafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "trackingIdempotencyStaleClaimAge", "PT15M");
        ReflectionTestUtils.setField(config, "trackingIngestedRetryBuffer", "PT30S");
        ReflectionTestUtils.setField(config, "trackingIngestedRetryMaxInterval", "PT30S");

        assertThrows(IllegalStateException.class, config::effectiveRetryMaxElapsedTime);
    }
}
