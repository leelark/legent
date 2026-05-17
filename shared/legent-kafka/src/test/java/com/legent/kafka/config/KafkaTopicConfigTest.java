package com.legent.kafka.config;

import com.legent.common.constant.AppConstants;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
