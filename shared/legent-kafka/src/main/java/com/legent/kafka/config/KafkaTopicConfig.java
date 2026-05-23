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
    public NewTopic kafkaDeadLetterTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_KAFKA_DLQ)
                .partitions(KafkaConsumerConfig.DEFAULT_DLQ_PARTITIONS)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailSentTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_EMAIL_SENT)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailFailedTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_EMAIL_FAILED)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailFailedDlqTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_EMAIL_FAILED_DLQ)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailRetryScheduledTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED)
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
    public NewTopic emailUnsubscribedTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_EMAIL_UNSUBSCRIBED)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailOpenTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_EMAIL_OPEN)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailClickTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_EMAIL_CLICK)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic conversionEventTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_CONVERSION_EVENT)
                .partitions(6)
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
    public NewTopic emailSendRequestedTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_EMAIL_SEND_REQUESTED)
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

    @Bean
    public NewTopic audienceResolutionRequestedTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic audienceResolvedTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_AUDIENCE_RESOLVED)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic batchCreatedTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_BATCH_CREATED)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic batchCompletedTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_BATCH_COMPLETED)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sendCompletedTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_SEND_COMPLETED)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sendFailedTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_SEND_FAILED)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sendProcessingTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_SEND_PROCESSING)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic subscriberCreatedTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_SUBSCRIBER_CREATED)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic subscriberUpdatedTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_SUBSCRIBER_UPDATED)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic subscriberDeletedTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_SUBSCRIBER_DELETED)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic workflowTriggerTopic() {
        return TopicBuilder.name(AppConstants.TOPIC_WORKFLOW_TRIGGER)
                .partitions(6)
                .replicas(1)
                .build();
    }
}
