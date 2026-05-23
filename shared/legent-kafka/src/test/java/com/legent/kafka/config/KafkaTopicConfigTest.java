package com.legent.kafka.config;

import com.legent.common.constant.AppConstants;
import com.legent.kafka.producer.EventPublisher;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaTopicConfigTest {

    private static final int HIGH_VOLUME_PARTITIONS = 6;
    private static final short LOCAL_REPLICATION_FACTOR = 1;

    @Test
    void sendPipelineTopicsUseExplicitHighVolumeDefinitions() {
        KafkaTopicConfig config = new KafkaTopicConfig();

        Map<String, NewTopic> topics = Map.of(
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED, config.emailSendRequestedTopic(),
                AppConstants.TOPIC_AUDIENCE_RESOLVED, config.audienceResolvedTopic(),
                AppConstants.TOPIC_BATCH_CREATED, config.batchCreatedTopic(),
                AppConstants.TOPIC_SEND_PROCESSING, config.sendProcessingTopic()
        );

        topics.forEach((name, topic) -> {
            assertEquals(name, topic.name());
            assertEquals(HIGH_VOLUME_PARTITIONS, topic.numPartitions());
            assertEquals(LOCAL_REPLICATION_FACTOR, topic.replicationFactor());
        });
    }

    @Test
    void deadLetterTopicSupportsHighVolumePartitionDistribution() {
        KafkaTopicConfig config = new KafkaTopicConfig();

        NewTopic topic = config.kafkaDeadLetterTopic();

        assertEquals(AppConstants.TOPIC_KAFKA_DLQ, topic.name());
        assertEquals(KafkaConsumerConfig.DEFAULT_DLQ_PARTITIONS, topic.numPartitions());
        assertTrue(topic.numPartitions() > 1);
        assertEquals(LOCAL_REPLICATION_FACTOR, topic.replicationFactor());
    }

    @Test
    void configuredSourceTopicsFitInsideDlqPartitionRange() {
        KafkaTopicConfig config = new KafkaTopicConfig();
        NewTopic dlqTopic = config.kafkaDeadLetterTopic();
        List<NewTopic> sourceTopics = configuredSourceTopics(config);

        sourceTopics.forEach(topic -> assertTrue(
                topic.numPartitions() <= dlqTopic.numPartitions(),
                () -> "DLQ topic must have at least as many partitions as " + topic.name()
        ));
    }

    @Test
    void eventPublisherHighVolumeTopicsHaveLocalTopicDefinitions() {
        KafkaTopicConfig config = new KafkaTopicConfig();

        Set<String> configuredTopicNames = configuredSourceTopics(config).stream()
                .map(NewTopic::name)
                .collect(Collectors.toSet());

        assertTrue(configuredTopicNames.containsAll(EventPublisher.highVolumeTopics()),
                () -> "Missing local topic definitions for "
                        + EventPublisher.highVolumeTopics().stream()
                                .filter(topic -> !configuredTopicNames.contains(topic))
                                .sorted()
                                .toList());
    }

    private List<NewTopic> configuredSourceTopics(KafkaTopicConfig config) {
        return List.of(
                config.emailSentTopic(),
                config.emailFailedTopic(),
                config.emailFailedDlqTopic(),
                config.emailRetryScheduledTopic(),
                config.emailBouncedTopic(),
                config.emailComplaintTopic(),
                config.emailDeliveredTopic(),
                config.emailUnsubscribedTopic(),
                config.emailOpenTopic(),
                config.emailClickTopic(),
                config.conversionEventTopic(),
                config.trackingIngestedTopic(),
                config.emailSendRequestedTopic(),
                config.sendRequestedTopic(),
                config.audienceResolutionRequestedTopic(),
                config.audienceResolvedTopic(),
                config.batchCreatedTopic(),
                config.batchCompletedTopic(),
                config.sendCompletedTopic(),
                config.sendFailedTopic(),
                config.sendProcessingTopic(),
                config.subscriberCreatedTopic(),
                config.subscriberUpdatedTopic(),
                config.subscriberDeletedTopic(),
                config.workflowTriggerTopic()
        );
    }
}
