package com.legent.automation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowEventPublisherTest {

    @Mock private EventPublisher eventPublisher;

    private WorkflowEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new WorkflowEventPublisher(eventPublisher, new ObjectMapper());
    }

    @Test
    void publishActionWaitsForKafkaSend() {
        when(eventPublisher.publish(
                eq(AppConstants.TOPIC_WORKFLOW_STARTED),
                eq("instance-1"),
                any(EventEnvelope.class))).thenReturn(CompletableFuture.<SendResult<String, Object>>completedFuture(null));

        publisher.publishAction(
                AppConstants.TOPIC_WORKFLOW_STARTED,
                "tenant-1",
                "instance-1",
                Map.of("instanceId", "instance-1"));

        verify(eventPublisher).publish(
                eq(AppConstants.TOPIC_WORKFLOW_STARTED),
                eq("instance-1"),
                any(EventEnvelope.class));
    }

    @Test
    void publishActionRethrowsKafkaSendFailure() {
        RuntimeException failure = new IllegalStateException("kafka unavailable");
        when(eventPublisher.publish(
                eq(AppConstants.TOPIC_WORKFLOW_STARTED),
                eq("instance-1"),
                any(EventEnvelope.class))).thenReturn(CompletableFuture.failedFuture(failure));

        assertThatThrownBy(() -> publisher.publishAction(
                AppConstants.TOPIC_WORKFLOW_STARTED,
                "tenant-1",
                "instance-1",
                Map.of("instanceId", "instance-1")))
                .isSameAs(failure);
    }
}
