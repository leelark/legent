package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.PersonalizationEvaluateRequest;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.foundation.service.performance.RealtimePersonalizationService;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalizationPerformanceServiceTest {

    @Mock
    private CorePlatformRepository repository;

    private RealtimePersonalizationService service;

    @BeforeEach
    void setUp() {
        service = new RealtimePersonalizationService(repository, new ObjectMapper());
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void evaluate_returnsSegmentVariantAndSloState() {
        when(repository.insert(anyString(), any(Map.class), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        PersonalizationEvaluateRequest request = new PersonalizationEvaluateRequest();
        request.setEvaluationKey("homepage-next-best-action");
        request.setSubjectId("sub-1");
        request.setEventType("profile.updated");
        request.setProfile(Map.of("interest", "upgrade", "consent", "OPT_IN", "lifecycleStage", "ENGAGED"));
        request.setEvent(Map.of("channel", "EMAIL"));
        request.setSegmentRules(List.of(Map.of(
                "key", "engaged-upgrade",
                "name", "Engaged upgrade",
                "rules", Map.of("conditions", List.of(Map.of("field", "interest", "op", "EQUALS", "value", "upgrade")))
        )));
        request.setVariants(List.of(
                Map.of("key", "generic", "channel", "EMAIL", "weight", 10),
                Map.of("key", "upgrade-offer", "channel", "EMAIL", "weight", 50, "tag", "upgrade", "segmentKey", "engaged-upgrade")
        ));
        request.setGuardrails(Map.of("consentRequired", true));
        request.setSimulatedLatencyMs(120);

        Map<String, Object> pass = service.evaluate(request);

        assertThat(pass.get("sloPass")).isEqualTo(true);
        assertThat(pass.get("latencyMs")).isEqualTo(120);
        assertThat((List<?>) pass.get("segmentHits")).hasSize(1);
        assertThat(((Map<?, ?>) pass.get("variantDecision")).get("variantKey")).isEqualTo("upgrade-offer");

        request.setSimulatedLatencyMs(1200);
        Map<String, Object> fail = service.evaluate(request);

        assertThat(fail.get("sloPass")).isEqualTo(false);
        assertThat(fail.get("latencyMs")).isEqualTo(1200);
    }
}
