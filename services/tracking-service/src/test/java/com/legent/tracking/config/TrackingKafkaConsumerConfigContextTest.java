package com.legent.tracking.config;

import com.legent.common.constant.AppConstants;
import com.legent.tracking.event.TrackingEventConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TrackingKafkaConsumerConfigContextTest {

    private static final String FACTORY_BEAN_NAME = "trackingIngestedKafkaListenerContainerFactory";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues(
                    "spring.kafka.listener.auto-startup=false",
                    "legent.tracking.kafka.ingested.listener.concurrency=2",
                    "legent.tracking.kafka.ingested.consumer.max-poll-records=25")
            .withUserConfiguration(TrackingKafkaConsumerConfig.class, MockKafkaDependencies.class);

    @Test
    void trackingIngestedFactoryRegistersByNameWithBatchSemanticsAndMockedDependencies() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean(FACTORY_BEAN_NAME);

            ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                    context.getBean(FACTORY_BEAN_NAME, ConcurrentKafkaListenerContainerFactory.class);
            ConsumerFactory<?, ?> consumerFactory = context.getBean(ConsumerFactory.class);
            KafkaTemplate<?, ?> kafkaTemplate = context.getBean(KafkaTemplate.class);
            DefaultErrorHandler sharedErrorHandler = context.getBean(DefaultErrorHandler.class);

            assertThat(context.getBeansOfType(DefaultErrorHandler.class)).hasSize(1);
            assertThat(factory.getConsumerFactory()).isSameAs(consumerFactory);
            assertThat(new DirectFieldAccessor(factory).getPropertyValue("commonErrorHandler"))
                    .isInstanceOf(DefaultErrorHandler.class)
                    .isNotSameAs(sharedErrorHandler);
            assertThat(factory.isBatchListener()).isEqualTo(Boolean.TRUE);
            assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.BATCH);
            assertThat(new DirectFieldAccessor(factory).getPropertyValue("concurrency")).isEqualTo(2);
            assertThat(factory.getContainerProperties().getKafkaConsumerProperties())
                    .containsEntry("max.poll.records", "25")
                    .containsEntry("max.poll.interval.ms", "1200000");
            assertThat(context).doesNotHaveBean(KafkaListenerEndpointRegistry.class);
        });
    }

    @Test
    void trackingEventConsumerListenerAnnotationReferencesTrackingIngestedFactoryBean() throws Exception {
        Method batchListener = TrackingEventConsumer.class.getDeclaredMethod("handleIngestedEvents", List.class);

        KafkaListener annotation = batchListener.getAnnotation(KafkaListener.class);

        assertThat(annotation).isNotNull();
        assertThat(Arrays.asList(annotation.topics())).contains(AppConstants.TOPIC_TRACKING_INGESTED);
        assertThat(annotation.containerFactory()).isEqualTo(FACTORY_BEAN_NAME);
    }

    @Configuration(proxyBeanMethods = false)
    static class MockKafkaDependencies {

        @Bean
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, Object> consumerFactory() {
            return mock(ConsumerFactory.class);
        }

        @Bean
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }

        @Bean
        DefaultErrorHandler kafkaErrorHandler() {
            return mock(DefaultErrorHandler.class);
        }
    }
}
