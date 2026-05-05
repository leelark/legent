package com.legent.kafka.config;

import com.legent.common.constant.AppConstants;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Proper Kafka topic initialization via Spring beans.
 * This ensures topics are created as soon as the service connects to Kafka.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic emailSentTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_EMAIL_SENT)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailBouncedTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_EMAIL_BOUNCED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailComplaintTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_EMAIL_COMPLAINT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailDeliveredTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_EMAIL_DELIVERED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic trackingIngestedTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_TRACKING_INGESTED)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sendRequestedTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_SEND_REQUESTED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
