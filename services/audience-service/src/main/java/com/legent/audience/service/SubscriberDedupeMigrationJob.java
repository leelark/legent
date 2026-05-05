package com.legent.audience.service;

import com.legent.audience.domain.Subscriber;
import com.legent.audience.dto.SubscriberDto;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriberDedupeMigrationJob implements ApplicationRunner {

    private final SubscriberRepository subscriberRepository;
    private final SubscriberService subscriberService;

    @Value("${legent.audience.dedupe-migration.enabled:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Subscriber dedupe migration job disabled");
            return;
        }

        List<Object[]> scopes = subscriberRepository.findDistinctTenantWorkspaceScopes();
        for (Object[] scope : scopes) {
            String tenantId = scope[0] == null ? null : String.valueOf(scope[0]);
            String workspaceId = scope[1] == null ? null : String.valueOf(scope[1]);
            if (tenantId == null || workspaceId == null) continue;
            processScope(tenantId, workspaceId);
        }
    }

    private void processScope(String tenantId, String workspaceId) {
        TenantContext.setTenantId(tenantId);
        TenantContext.setWorkspaceId(workspaceId);
        try {
            List<Object[]> duplicates = subscriberRepository.findDuplicateEmailsByScope(tenantId, workspaceId);
            for (Object[] row : duplicates) {
                String email = row[0] == null ? null : String.valueOf(row[0]).toLowerCase(Locale.ROOT);
                if (email == null) continue;
                List<Subscriber> group = subscriberRepository.findDuplicatesByScopedEmail(tenantId, workspaceId, email);
                if (group.size() <= 1) continue;

                Subscriber winner = group.stream()
                        .filter(s -> s.getStatus() == Subscriber.SubscriberStatus.ACTIVE || s.getStatus() == Subscriber.SubscriberStatus.SUBSCRIBED)
                        .max(Comparator.comparing(Subscriber::getUpdatedAt))
                        .orElse(group.get(0));

                List<String> losers = group.stream()
                        .filter(s -> !s.getId().equals(winner.getId()))
                        .map(Subscriber::getId)
                        .collect(Collectors.toList());
                if (losers.isEmpty()) continue;

                subscriberService.merge(SubscriberDto.MergeRequest.builder()
                        .winnerSubscriberId(winner.getId())
                        .mergedSubscriberIds(losers)
                        .reason("AUTO_DEDUPE_EMAIL_SCOPE")
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed dedupe migration for tenant={} workspace={}", tenantId, workspaceId, e);
        } finally {
            TenantContext.clear();
        }
    }
}
