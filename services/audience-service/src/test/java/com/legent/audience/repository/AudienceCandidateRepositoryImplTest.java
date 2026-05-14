package com.legent.audience.repository;

import com.legent.audience.domain.ListMembership;
import com.legent.audience.domain.SegmentMembership;
import com.legent.audience.domain.Subscriber;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(AudienceCandidateRepositoryImpl.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AudienceCandidateRepositoryImplTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String OTHER_TENANT_ID = "tenant-b";
    private static final String WORKSPACE_ID = "workspace-a";
    private static final String OTHER_WORKSPACE_ID = "workspace-b";
    private static final String LIST_ID = "list-a";
    private static final String OTHER_LIST_ID = "list-b";
    private static final String SEGMENT_ID = "segment-a";
    private static final String OTHER_SEGMENT_ID = "segment-b";

    @Autowired
    private AudienceCandidateRepository repository;

    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:audience_candidates;"
                + "MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;"
                + "INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Test
    void includeListReturnsOnlyActiveListMemberships() {
        Subscriber active = subscriber("00000000000000000000000001", "active@example.test");
        Subscriber removed = subscriber("00000000000000000000000002", "removed@example.test");
        persist(active, removed);
        persist(
                listMembership(active, LIST_ID, ListMembership.MembershipStatus.ACTIVE),
                listMembership(removed, LIST_ID, ListMembership.MembershipStatus.REMOVED));
        flushAndClear();

        List<Subscriber> candidates = find(criteria(false, Set.of(LIST_ID), Set.of(), Set.of(), Set.of()));

        assertThat(ids(candidates)).containsExactly(active.getId());
    }

    @Test
    void includeSegmentReturnsSegmentMembers() {
        Subscriber member = subscriber("00000000000000000000000003", "member@example.test");
        Subscriber nonMember = subscriber("00000000000000000000000004", "non-member@example.test");
        persist(member, nonMember);
        persist(segmentMembership(member, SEGMENT_ID));
        flushAndClear();

        List<Subscriber> candidates = find(criteria(false, Set.of(), Set.of(SEGMENT_ID), Set.of(), Set.of()));

        assertThat(ids(candidates)).containsExactly(member.getId());
    }

    @Test
    void excludeOnlyWithoutIncludeReturnsNoCandidates() {
        Subscriber subscriber = subscriber("00000000000000000000000005", "subscriber@example.test");
        persist(subscriber);
        persist(listMembership(subscriber, LIST_ID, ListMembership.MembershipStatus.ACTIVE));
        flushAndClear();

        List<Subscriber> candidates = find(criteria(false, Set.of(), Set.of(), Set.of(LIST_ID), Set.of()));

        assertThat(candidates).isEmpty();
    }

    @Test
    void includeAllSubscribersReturnsWorkspaceSubscribersMinusExclusionListsAndSegments() {
        Subscriber kept = subscriber("00000000000000000000000017", "include-all-kept@example.test");
        Subscriber excludedByList = subscriber("00000000000000000000000018", "include-all-list@example.test");
        Subscriber excludedBySegment = subscriber("00000000000000000000000019", "include-all-segment@example.test");
        Subscriber removedListMembership = subscriber("00000000000000000000000020", "include-all-removed-list@example.test");
        Subscriber otherWorkspaceSubscriber = subscriber(
                "00000000000000000000000021",
                "include-all-other-workspace@example.test",
                TENANT_ID,
                OTHER_WORKSPACE_ID);
        persist(kept, excludedByList, excludedBySegment, removedListMembership, otherWorkspaceSubscriber);
        persist(
                listMembership(excludedByList, LIST_ID, ListMembership.MembershipStatus.ACTIVE),
                listMembership(removedListMembership, LIST_ID, ListMembership.MembershipStatus.REMOVED),
                listMembership(otherWorkspaceSubscriber, LIST_ID, ListMembership.MembershipStatus.ACTIVE),
                segmentMembership(excludedBySegment, SEGMENT_ID));
        flushAndClear();

        List<Subscriber> candidates = find(criteria(
                true,
                Set.of(),
                Set.of(),
                Set.of(LIST_ID),
                Set.of(SEGMENT_ID)));

        assertThat(ids(candidates)).containsExactly(kept.getId(), removedListMembership.getId());
    }

    @Test
    void mixedIncludeAndExcludeRemovesExcludedMembers() {
        Subscriber kept = subscriber("00000000000000000000000006", "kept@example.test");
        Subscriber excludedBySegment = subscriber("00000000000000000000000007", "excluded@example.test");
        Subscriber excludedByList = subscriber("00000000000000000000000008", "excluded-list@example.test");
        persist(kept, excludedBySegment, excludedByList);
        persist(
                listMembership(kept, LIST_ID, ListMembership.MembershipStatus.ACTIVE),
                listMembership(excludedBySegment, LIST_ID, ListMembership.MembershipStatus.ACTIVE),
                listMembership(excludedByList, LIST_ID, ListMembership.MembershipStatus.ACTIVE),
                listMembership(excludedByList, OTHER_LIST_ID, ListMembership.MembershipStatus.ACTIVE),
                segmentMembership(excludedBySegment, SEGMENT_ID));
        flushAndClear();

        List<Subscriber> candidates = find(criteria(
                false,
                Set.of(LIST_ID),
                Set.of(),
                Set.of(OTHER_LIST_ID),
                Set.of(SEGMENT_ID)));

        assertThat(ids(candidates)).containsExactly(kept.getId());
    }

    @Test
    void deletedSubscribersAreSkipped() {
        Subscriber active = subscriber("00000000000000000000000009", "active-soft-delete@example.test");
        Subscriber deleted = subscriber("00000000000000000000000010", "deleted@example.test");
        deleted.setDeletedAt(Instant.now());
        persist(active, deleted);
        persist(
                listMembership(active, LIST_ID, ListMembership.MembershipStatus.ACTIVE),
                listMembership(deleted, LIST_ID, ListMembership.MembershipStatus.ACTIVE));
        flushAndClear();

        List<Subscriber> candidates = find(criteria(false, Set.of(LIST_ID), Set.of(), Set.of(), Set.of()));

        assertThat(ids(candidates)).containsExactly(active.getId());
    }

    @Test
    void workspaceIsolationPreventsCrossWorkspaceCandidates() {
        Subscriber workspaceSubscriber = subscriber("00000000000000000000000011", "workspace@example.test");
        Subscriber otherWorkspaceSubscriber = subscriber(
                "00000000000000000000000012",
                "other-workspace@example.test",
                TENANT_ID,
                OTHER_WORKSPACE_ID);
        Subscriber otherTenantSubscriber = subscriber(
                "00000000000000000000000013",
                "other-tenant@example.test",
                OTHER_TENANT_ID,
                WORKSPACE_ID);
        persist(workspaceSubscriber, otherWorkspaceSubscriber, otherTenantSubscriber);
        persist(
                listMembership(workspaceSubscriber, LIST_ID, ListMembership.MembershipStatus.ACTIVE),
                listMembership(otherWorkspaceSubscriber, LIST_ID, ListMembership.MembershipStatus.ACTIVE),
                listMembership(otherTenantSubscriber, LIST_ID, ListMembership.MembershipStatus.ACTIVE));
        flushAndClear();

        List<Subscriber> candidates = find(criteria(false, Set.of(LIST_ID), Set.of(), Set.of(), Set.of()));

        assertThat(ids(candidates)).containsExactly(workspaceSubscriber.getId());
    }

    @Test
    void keysetPaginationReturnsRowsAfterLastSeenIdInIdOrder() {
        Subscriber first = subscriber("00000000000000000000000014", "first@example.test");
        Subscriber second = subscriber("00000000000000000000000015", "second@example.test");
        Subscriber third = subscriber("00000000000000000000000016", "third@example.test");
        persist(first, second, third);
        persist(
                listMembership(first, LIST_ID, ListMembership.MembershipStatus.ACTIVE),
                listMembership(second, LIST_ID, ListMembership.MembershipStatus.ACTIVE),
                listMembership(third, LIST_ID, ListMembership.MembershipStatus.ACTIVE));
        flushAndClear();

        List<Subscriber> candidates = repository.findNextCandidates(
                TENANT_ID,
                WORKSPACE_ID,
                criteria(false, Set.of(LIST_ID), Set.of(), Set.of(), Set.of()),
                first.getId(),
                PageRequest.of(0, 1));

        assertThat(ids(candidates)).containsExactly(second.getId());
    }

    private List<Subscriber> find(AudienceCandidateRepository.AudienceCandidateCriteria criteria) {
        return repository.findNextCandidates(TENANT_ID, WORKSPACE_ID, criteria, null, PageRequest.of(0, 50));
    }

    private AudienceCandidateRepository.AudienceCandidateCriteria criteria(
            boolean includeAllSubscribers,
            Set<String> includeListIds,
            Set<String> includeSegmentIds,
            Set<String> excludeListIds,
            Set<String> excludeSegmentIds) {
        return new AudienceCandidateRepository.AudienceCandidateCriteria(
                includeAllSubscribers,
                includeListIds,
                includeSegmentIds,
                excludeListIds,
                excludeSegmentIds);
    }

    private Subscriber subscriber(String id, String email) {
        return subscriber(id, email, TENANT_ID, WORKSPACE_ID);
    }

    private Subscriber subscriber(String id, String email, String tenantId, String workspaceId) {
        Subscriber subscriber = new Subscriber();
        subscriber.setId(id);
        subscriber.setTenantId(tenantId);
        subscriber.setWorkspaceId(workspaceId);
        subscriber.setSubscriberKey(id);
        subscriber.setEmail(email);
        return subscriber;
    }

    private ListMembership listMembership(
            Subscriber subscriber,
            String listId,
            ListMembership.MembershipStatus status) {
        ListMembership membership = new ListMembership();
        membership.setTenantId(subscriber.getTenantId());
        membership.setWorkspaceId(subscriber.getWorkspaceId());
        membership.setSubscriberId(subscriber.getId());
        membership.setListId(listId);
        membership.setStatus(status);
        return membership;
    }

    private SegmentMembership segmentMembership(Subscriber subscriber, String segmentId) {
        SegmentMembership membership = new SegmentMembership();
        membership.setTenantId(subscriber.getTenantId());
        membership.setWorkspaceId(subscriber.getWorkspaceId());
        membership.setSubscriberId(subscriber.getId());
        membership.setSegmentId(segmentId);
        return membership;
    }

    private void persist(Object... entities) {
        for (Object entity : entities) {
            entityManager.persist(entity);
        }
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private List<String> ids(List<Subscriber> subscribers) {
        return subscribers.stream()
                .map(Subscriber::getId)
                .toList();
    }
}
