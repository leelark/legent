package com.legent.audience.event;

import com.legent.common.constant.AppConstants;

import java.util.Map;

import com.legent.audience.domain.ImportJob;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class ImportEventPublisher {

    private final EventPublisher eventPublisher;
    private static final String SOURCE = "audience-service";

    public void publishStarted(ImportJob job) {
        publish(AppConstants.TOPIC_IMPORT_STARTED, job, Map.of(
                "jobId", job.getId(), "fileName", job.getFileName(), "targetType", job.getTargetType()));
    }

    public void publishCompleted(ImportJob job) {
        publish(AppConstants.TOPIC_IMPORT_COMPLETED, job, Map.of(
                "jobId", job.getId(), "successRows", String.valueOf(job.getSuccessRows()),
                "errorRows", String.valueOf(job.getErrorRows())));
    }

    public void publishFailed(ImportJob job, String reason) {
        publish(AppConstants.TOPIC_IMPORT_FAILED, job, Map.of(
                "jobId", job.getId(), "reason", reason));
    }

    private void publish(String topic, ImportJob job, Map<String, String> payload) {
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(topic, job.getTenantId(), SOURCE, payload);
        eventPublisher.publish(topic, envelope);
    }
}
