package com.legent.audience.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AudienceKafkaConsumerConfigTest {

    @Test
    void audienceTrackingFactoryUsesBatchListenerBatchAckConcurrencyAndBoundedPollRecords() {
        AudienceKafkaConsumerConfig config = new AudienceKafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "trackingIngestedListenerConcurrency", 4);
        ReflectionTestUtils.setField(config, "trackingIngestedMaxPollRecords", 250);
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, Object> consumerFactory = mock(ConsumerFactory.class);
        DefaultErrorHandler errorHandler = mock(DefaultErrorHandler.class);

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                config.audienceTrackingIngestedKafkaListenerContainerFactory(consumerFactory, errorHandler);

        assertThat(factory.getConsumerFactory()).isEqualTo(consumerFactory);
        assertThat(factory.isBatchListener()).isTrue();
        assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.BATCH);
        assertThat(new DirectFieldAccessor(factory).getPropertyValue("concurrency")).isEqualTo(4);
        assertThat(new DirectFieldAccessor(factory).getPropertyValue("commonErrorHandler")).isEqualTo(errorHandler);
        assertThat(factory.getContainerProperties()
                .getKafkaConsumerProperties()
                .getProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG)).isEqualTo("250");
    }

    @Test
    void audienceTrackingFactoryFloorsInvalidConcurrencyAndPollRecordsAtOne() {
        AudienceKafkaConsumerConfig config = new AudienceKafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "trackingIngestedListenerConcurrency", 0);
        ReflectionTestUtils.setField(config, "trackingIngestedMaxPollRecords", -25);
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, Object> consumerFactory = mock(ConsumerFactory.class);
        DefaultErrorHandler errorHandler = mock(DefaultErrorHandler.class);

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                config.audienceTrackingIngestedKafkaListenerContainerFactory(consumerFactory, errorHandler);

        assertThat(new DirectFieldAccessor(factory).getPropertyValue("concurrency")).isEqualTo(1);
        assertThat(factory.getContainerProperties()
                .getKafkaConsumerProperties()
                .getProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG)).isEqualTo("1");
    }
}
