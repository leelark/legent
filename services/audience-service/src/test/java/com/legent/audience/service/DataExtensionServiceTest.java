package com.legent.audience.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.audience.domain.DataExtension;
import com.legent.audience.domain.DataExtensionField;
import com.legent.audience.domain.DataExtensionGovernanceAudit;
import com.legent.audience.domain.DataExtensionRecord;
import com.legent.audience.domain.DataExtensionRelationship;
import com.legent.audience.dto.DataExtensionDto;
import com.legent.audience.repository.DataExtensionFieldRepository;
import com.legent.audience.repository.DataExtensionGovernanceAuditRepository;
import com.legent.audience.repository.DataExtensionRecordRepository;
import com.legent.audience.repository.DataExtensionRepository;
import com.legent.audience.repository.DataExtensionRelationshipRepository;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataExtensionServiceTest {

    @Mock private DataExtensionRepository deRepository;
    @Mock private DataExtensionFieldRepository fieldRepository;
    @Mock private DataExtensionRecordRepository recordRepository;
    @Mock private DataExtensionRelationshipRepository relationshipRepository;
    @Mock private DataExtensionGovernanceAuditRepository governanceAuditRepository;
    @Mock private ContactLifecycleAuditService lifecycleAuditService;

    private DataExtensionService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");
        service = new DataExtensionService(deRepository, fieldRepository, recordRepository, relationshipRepository,
                governanceAuditRepository, lifecycleAuditService, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listRecordsUsesTenantScopedRepository() {
        DataExtension de = dataExtension();
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1")).thenReturn(Optional.of(de));
        when(recordRepository.findByTenantIdAndWorkspaceIdAndDataExtensionId(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listRecords("de-1", PageRequest.of(0, 20));

        verify(recordRepository).findByTenantIdAndWorkspaceIdAndDataExtensionId("tenant-1", "workspace-1", "de-1", PageRequest.of(0, 20));
    }

    @Test
    void getByIdRequiresSameTenantAndWorkspace() {
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-2"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("de-2"))
                .isInstanceOf(NotFoundException.class);

        verify(deRepository).findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-2");
    }

    @Test
    void sameNameIsAllowedAcrossWorkspaces() {
        TenantContext.setWorkspaceId("workspace-2");
        when(deRepository.existsByTenantWorkspaceAndName("tenant-1", "workspace-2", "Customers"))
                .thenReturn(false);
        when(deRepository.save(any(DataExtension.class))).thenAnswer(invocation -> {
            DataExtension saved = invocation.getArgument(0);
            saved.setId("de-2");
            return saved;
        });

        DataExtensionDto.Response response = service.create(DataExtensionDto.CreateRequest.builder()
                .name("Customers")
                .fields(List.of(DataExtensionDto.FieldDefinition.builder()
                        .fieldName("email")
                        .fieldType("EMAIL")
                        .required(true)
                        .build()))
                .build());

        assertThat(response.getId()).isEqualTo("de-2");
        verify(deRepository).existsByTenantWorkspaceAndName("tenant-1", "workspace-2", "Customers");
    }

    @Test
    void importMappingPreviewReportsMissingRequiredField() {
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1")).thenReturn(Optional.of(dataExtension()));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());

        DataExtensionDto.ImportMappingPreviewResponse response = service.previewImportMapping("de-1",
                DataExtensionDto.ImportMappingPreviewRequest.builder()
                        .sourceHeaders(List.of("subscriber_key"))
                        .fieldMapping(Map.of("subscriberKey", "subscriber_key"))
                        .sampleRows(List.of(Map.of("subscriber_key", "abc")))
                        .build());

        assertThat(response.isValid()).isFalse();
        assertThat(response.getErrors()).anyMatch(error -> error.contains("Required field is not mapped: email"));
    }

    @Test
    void queryPreviewFiltersAndProjectsRows() {
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1")).thenReturn(Optional.of(dataExtension()));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());
        DataExtensionRecord matching = record(Map.of("subscriberKey", "abc", "email", "a@example.com", "score", 12L));
        DataExtensionRecord skipped = record(Map.of("subscriberKey", "def", "email", "b@example.com", "score", 1L));
        when(recordRepository.findByTenantIdAndWorkspaceIdAndDataExtensionId(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(matching, skipped)));
        when(recordRepository.countByTenantWorkspaceAndDataExtension("tenant-1", "workspace-1", "de-1")).thenReturn(2L);

        DataExtensionDto.QueryPreviewResponse response = service.previewQuery("de-1",
                DataExtensionDto.QueryPreviewRequest.builder()
                        .fields(List.of("email"))
                        .filters(List.of(DataExtensionDto.QueryFilter.builder()
                                .fieldName("score")
                                .operator("GT")
                                .value(10)
                                .build()))
                        .limit(100)
                        .build());

        assertThat(response.getReturnedRows()).isEqualTo(1);
        assertThat(response.getRows()).containsExactly(Map.of("email", "a@example.com"));
    }

    @Test
    void queryPreviewScansPastFirstPageForFilteredMatches() {
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1")).thenReturn(Optional.of(dataExtension()));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());
        List<DataExtensionRecord> skippedRows = IntStream.range(0, 500)
                .mapToObj(index -> record(Map.of(
                        "subscriberKey", "skip-" + index,
                        "email", "b" + index + "@example.com",
                        "score", 1L)))
                .toList();
        DataExtensionRecord matching = record(Map.of("subscriberKey", "abc", "email", "a@example.com", "score", 12L));
        when(recordRepository.findByTenantIdAndWorkspaceIdAndDataExtensionId("tenant-1", "workspace-1", "de-1", PageRequest.of(0, 500)))
                .thenReturn(new PageImpl<>(skippedRows, PageRequest.of(0, 500), 501));
        when(recordRepository.findByTenantIdAndWorkspaceIdAndDataExtensionId("tenant-1", "workspace-1", "de-1", PageRequest.of(1, 500)))
                .thenReturn(new PageImpl<>(List.of(matching), PageRequest.of(1, 500), 501));
        when(recordRepository.countByTenantWorkspaceAndDataExtension("tenant-1", "workspace-1", "de-1")).thenReturn(501L);

        DataExtensionDto.QueryPreviewResponse response = service.previewQuery("de-1",
                DataExtensionDto.QueryPreviewRequest.builder()
                        .fields(List.of("email"))
                        .filters(List.of(DataExtensionDto.QueryFilter.builder()
                                .fieldName("score")
                                .operator("GT")
                                .value(10)
                                .build()))
                        .limit(1)
                        .build());

        assertThat(response.getReturnedRows()).isEqualTo(1);
        assertThat(response.getRows()).containsExactly(Map.of("email", "a@example.com"));
        assertThat(response.getScannedRows()).isEqualTo(501);
    }

    @Test
    void queryPreviewSortsBeforeProjection() {
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1")).thenReturn(Optional.of(dataExtension()));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());
        DataExtensionRecord lower = record(Map.of("subscriberKey", "low", "email", "low@example.com", "score", 1L));
        DataExtensionRecord higher = record(Map.of("subscriberKey", "high", "email", "high@example.com", "score", 50L));
        when(recordRepository.findByTenantIdAndWorkspaceIdAndDataExtensionId(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(lower, higher)));
        when(recordRepository.countByTenantWorkspaceAndDataExtension("tenant-1", "workspace-1", "de-1")).thenReturn(2L);

        DataExtensionDto.QueryPreviewResponse response = service.previewQuery("de-1",
                DataExtensionDto.QueryPreviewRequest.builder()
                        .fields(List.of("email"))
                        .sortField("score")
                        .sortDirection("DESC")
                        .limit(2)
                        .build());

        assertThat(response.getRows()).containsExactly(
                Map.of("email", "high@example.com"),
                Map.of("email", "low@example.com"));
    }

    @Test
    void queryPreviewRejectsUnknownSortField() {
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1")).thenReturn(Optional.of(dataExtension()));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());

        assertThatThrownBy(() -> service.previewQuery("de-1",
                DataExtensionDto.QueryPreviewRequest.builder()
                        .sortField("missingField")
                        .build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown field: missingField");
    }

    @Test
    void queryPreviewRejectsRelationshipPathFields() {
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1")).thenReturn(Optional.of(dataExtension()));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());

        assertThatThrownBy(() -> service.previewQuery("de-1",
                DataExtensionDto.QueryPreviewRequest.builder()
                        .fields(List.of("profile.email"))
                        .build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Relationship path fields are not supported");
    }

    @Test
    void updateRelationshipsRequiresTargetInSameTenantAndWorkspace() {
        DataExtension source = dataExtension();
        DataExtension target = targetDataExtension();
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(source));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1"))
                .thenReturn(fields(), fields());
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-target"))
                .thenReturn(Optional.of(target));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-target"))
                .thenReturn(List.of(field("subscriberKey", DataExtensionField.FieldType.TEXT, true, true)));
        when(deRepository.save(source)).thenReturn(source);

        DataExtensionDto.RelationshipRequest request = DataExtensionDto.RelationshipRequest.builder()
                .relationships(List.of(DataExtensionDto.RelationshipDefinition.builder()
                        .name("customer_profile")
                        .sourceField("subscriberKey")
                        .targetDataExtensionId("de-target")
                        .targetField("subscriberKey")
                        .cardinality("MANY_TO_ONE")
                        .build()))
                .build();

        DataExtensionDto.Response response = service.updateRelationships("de-1", request);

        assertThat(response.getRelationships()).hasSize(1);
        assertThat(source.getRelationshipJson()).contains("customer_profile");
        verify(deRepository).findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-target");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DataExtensionRelationship>> relationshipCaptor = ArgumentCaptor.forClass(List.class);
        verify(relationshipRepository).saveAll(relationshipCaptor.capture());
        DataExtensionRelationship savedRelationship = relationshipCaptor.getValue().get(0);
        assertThat(savedRelationship.getTenantId()).isEqualTo("tenant-1");
        assertThat(savedRelationship.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(savedRelationship.getSourceDataExtensionId()).isEqualTo("de-1");
        assertThat(savedRelationship.getTargetDataExtensionId()).isEqualTo("de-target");
        assertThat(savedRelationship.getName()).isEqualTo("customer_profile");
        assertThat(savedRelationship.getCardinality()).isEqualTo("MANY_TO_ONE");
        assertThat(savedRelationship.isActive()).isTrue();
        ArgumentCaptor<DataExtensionGovernanceAudit> auditCaptor = ArgumentCaptor.forClass(DataExtensionGovernanceAudit.class);
        verify(governanceAuditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("RELATIONSHIPS_UPDATED");
        assertThat(auditCaptor.getValue().getMetadata()).containsEntry("relationshipCount", 1);
    }

    @Test
    void updateRelationshipsRequiresWorkspaceContextBeforeRepositoryAccess() {
        TenantContext.clear();
        TenantContext.setTenantId("tenant-1");

        DataExtensionDto.RelationshipRequest request = DataExtensionDto.RelationshipRequest.builder()
                .relationships(List.of())
                .build();

        assertThatThrownBy(() -> service.updateRelationships("de-1", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workspace context is not set");

        verifyNoInteractions(deRepository, fieldRepository, relationshipRepository);
    }

    @Test
    void updateRelationshipsRejectsMissingSourceFieldBeforeTargetLookup() {
        DataExtension source = dataExtension();
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(source));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());

        DataExtensionDto.RelationshipRequest request = DataExtensionDto.RelationshipRequest.builder()
                .relationships(List.of(DataExtensionDto.RelationshipDefinition.builder()
                        .name("bad_source")
                        .sourceField("missingField")
                        .targetDataExtensionId("de-target")
                        .targetField("subscriberKey")
                        .cardinality("MANY_TO_ONE")
                        .build()))
                .build();

        assertThatThrownBy(() -> service.updateRelationships("de-1", request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Relationship source field does not exist");
    }

    @Test
    void updateRelationshipsRejectsMissingTargetField() {
        DataExtension source = dataExtension();
        DataExtension target = targetDataExtension();
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(source));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-target"))
                .thenReturn(Optional.of(target));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-target"))
                .thenReturn(List.of(field("externalId", DataExtensionField.FieldType.TEXT, true, true)));

        DataExtensionDto.RelationshipRequest request = DataExtensionDto.RelationshipRequest.builder()
                .relationships(List.of(DataExtensionDto.RelationshipDefinition.builder()
                        .name("bad_target")
                        .sourceField("subscriberKey")
                        .targetDataExtensionId("de-target")
                        .targetField("subscriberKey")
                        .cardinality("MANY_TO_ONE")
                        .build()))
                .build();

        assertThatThrownBy(() -> service.updateRelationships("de-1", request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Relationship target field does not exist");
    }

    @Test
    void updateRelationshipsRejectsMissingCardinality() {
        DataExtension source = dataExtension();
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(source));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());

        DataExtensionDto.RelationshipRequest request = DataExtensionDto.RelationshipRequest.builder()
                .relationships(List.of(DataExtensionDto.RelationshipDefinition.builder()
                        .name("missing_cardinality")
                        .sourceField("subscriberKey")
                        .targetDataExtensionId("de-target")
                        .targetField("subscriberKey")
                        .build()))
                .build();

        assertThatThrownBy(() -> service.updateRelationships("de-1", request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported relationship cardinality");
    }

    @Test
    void updateRelationshipsRejectsManyToOneWithoutTargetPrimaryKey() {
        DataExtension source = dataExtension();
        DataExtension target = targetDataExtension();
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(source));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-target"))
                .thenReturn(Optional.of(target));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-target"))
                .thenReturn(List.of(field("subscriberKey", DataExtensionField.FieldType.TEXT, true, false)));

        DataExtensionDto.RelationshipRequest request = DataExtensionDto.RelationshipRequest.builder()
                .relationships(List.of(DataExtensionDto.RelationshipDefinition.builder()
                        .name("customer_profile")
                        .sourceField("subscriberKey")
                        .targetDataExtensionId("de-target")
                        .targetField("subscriberKey")
                        .cardinality("MANY_TO_ONE")
                        .build()))
                .build();

        assertThatThrownBy(() -> service.updateRelationships("de-1", request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("targetField to be the target primary key");
    }

    @Test
    void updateRelationshipsRejectsTypeMismatch() {
        DataExtension source = dataExtension();
        DataExtension target = targetDataExtension();
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(source));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-target"))
                .thenReturn(Optional.of(target));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-target"))
                .thenReturn(List.of(field("subscriberKey", DataExtensionField.FieldType.NUMBER, true, true)));

        DataExtensionDto.RelationshipRequest request = DataExtensionDto.RelationshipRequest.builder()
                .relationships(List.of(DataExtensionDto.RelationshipDefinition.builder()
                        .name("customer_profile")
                        .sourceField("subscriberKey")
                        .targetDataExtensionId("de-target")
                        .targetField("subscriberKey")
                        .cardinality("MANY_TO_ONE")
                        .build()))
                .build();

        assertThatThrownBy(() -> service.updateRelationships("de-1", request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("field types must match");
    }

    @Test
    void updateSendableConfigRejectsUnknownSendableField() {
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(dataExtension()));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());

        DataExtensionDto.SendableConfigRequest request = DataExtensionDto.SendableConfigRequest.builder()
                .sendable(true)
                .sendableField("missingEmail")
                .primaryKeyField("subscriberKey")
                .build();

        assertThatThrownBy(() -> service.updateSendableConfig("de-1", request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("sendableField must reference an existing field");
    }

    @Test
    void createRejectsSendableFieldThatIsNotRequired() {
        when(deRepository.existsByTenantWorkspaceAndName("tenant-1", "workspace-1", "Customers"))
                .thenReturn(false);

        DataExtensionDto.CreateRequest request = DataExtensionDto.CreateRequest.builder()
                .name("Customers")
                .sendable(true)
                .sendableField("email")
                .primaryKeyField("subscriberKey")
                .fields(List.of(
                        DataExtensionDto.FieldDefinition.builder()
                                .fieldName("subscriberKey")
                                .fieldType("TEXT")
                                .required(true)
                                .primaryKey(true)
                                .build(),
                        DataExtensionDto.FieldDefinition.builder()
                                .fieldName("email")
                                .fieldType("EMAIL")
                                .required(false)
                                .build()))
                .build();

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("sendableField must be required");
    }

    @Test
    void updateSendableConfigRejectsChangeWhenRecordsExist() {
        DataExtension de = dataExtension();
        de.setSendable(true);
        de.setSendableField("email");
        de.setPrimaryKeyField("subscriberKey");
        de.setRecordCount(10);
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(de));

        DataExtensionDto.SendableConfigRequest request = DataExtensionDto.SendableConfigRequest.builder()
                .sendable(true)
                .sendableField("score")
                .primaryKeyField("subscriberKey")
                .build();

        assertThatThrownBy(() -> service.updateSendableConfig("de-1", request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be changed while records exist");
    }

    @Test
    void updateSendableConfigWritesAuditEntry() {
        DataExtension de = dataExtension();
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(de));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());
        when(deRepository.save(de)).thenReturn(de);

        DataExtensionDto.Response response = service.updateSendableConfig("de-1",
                DataExtensionDto.SendableConfigRequest.builder()
                        .sendable(true)
                        .sendableField("email")
                        .primaryKeyField("subscriberKey")
                        .build());

        assertThat(response.isSendable()).isTrue();
        assertThat(response.getSendableField()).isEqualTo("email");
        ArgumentCaptor<DataExtensionGovernanceAudit> auditCaptor = ArgumentCaptor.forClass(DataExtensionGovernanceAudit.class);
        verify(governanceAuditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("SENDABLE_UPDATED");
        assertThat(auditCaptor.getValue().getMetadata()).containsEntry("sendableField", "email");
    }

    @Test
    void updateRetentionPolicyNoneClearsRetentionFields() {
        DataExtension de = dataExtension();
        de.setRetentionDays(30);
        de.setRetentionAction("DELETE_RECORDS");
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(de));
        when(deRepository.save(de)).thenReturn(de);
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());

        DataExtensionDto.Response response = service.updateRetentionPolicy("de-1",
                DataExtensionDto.RetentionPolicyRequest.builder()
                        .retentionAction("NONE")
                        .retentionDays(365)
                        .build());

        assertThat(response.getRetentionAction()).isEqualTo("NONE");
        assertThat(response.getRetentionDays()).isNull();
        ArgumentCaptor<DataExtensionGovernanceAudit> auditCaptor = ArgumentCaptor.forClass(DataExtensionGovernanceAudit.class);
        verify(governanceAuditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("RETENTION_UPDATED");
        assertThat(auditCaptor.getValue().getMetadata()).containsEntry("retentionAction", "NONE");
    }

    @Test
    @DisplayName("create stores governance metadata field classification and audit entry")
    void createStoresGovernanceMetadataFieldClassificationAndAuditEntry() {
        when(deRepository.existsByTenantWorkspaceAndName("tenant-1", "workspace-1", "Customers"))
                .thenReturn(false);
        when(deRepository.save(any(DataExtension.class))).thenAnswer(invocation -> {
            DataExtension saved = invocation.getArgument(0);
            saved.setId("de-1");
            return saved;
        });

        DataExtensionDto.Response response = service.create(DataExtensionDto.CreateRequest.builder()
                .name("Customers")
                .governance(DataExtensionDto.GovernanceMetadata.builder()
                        .sourceType("IMPORT")
                        .sourceSystem("crm")
                        .sourceReference("import-job-1")
                        .dataClassification("CONFIDENTIAL")
                        .governanceNotes("Reviewed source")
                        .build())
                .fields(List.of(DataExtensionDto.FieldDefinition.builder()
                        .fieldName("email")
                        .fieldType("EMAIL")
                        .dataClassification("RESTRICTED")
                        .required(true)
                        .build()))
                .build());

        assertThat(response.getGovernance().getSourceType()).isEqualTo("IMPORT");
        assertThat(response.getGovernance().getDataClassification()).isEqualTo("CONFIDENTIAL");
        ArgumentCaptor<DataExtensionField> fieldCaptor = ArgumentCaptor.forClass(DataExtensionField.class);
        verify(fieldRepository).save(fieldCaptor.capture());
        assertThat(fieldCaptor.getValue().getDataClassification()).isEqualTo("RESTRICTED");
        ArgumentCaptor<DataExtensionGovernanceAudit> auditCaptor = ArgumentCaptor.forClass(DataExtensionGovernanceAudit.class);
        verify(governanceAuditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getTenantId()).isEqualTo("tenant-1");
        assertThat(auditCaptor.getValue().getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("CREATED");
        assertThat(auditCaptor.getValue().getMetadata()).containsEntry("dataClassification", "CONFIDENTIAL");
    }

    @Test
    void updateGovernanceRejectsUnsupportedClassification() {
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(dataExtension()));

        assertThatThrownBy(() -> service.updateGovernance("de-1",
                DataExtensionDto.GovernanceMetadata.builder()
                        .dataClassification("SECRET")
                        .build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported dataClassification");
    }

    @Test
    void updateGovernanceRejectsUnsupportedSourceType() {
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(dataExtension()));

        assertThatThrownBy(() -> service.updateGovernance("de-1",
                DataExtensionDto.GovernanceMetadata.builder()
                        .sourceType("SPREADSHEET")
                        .build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported sourceType");
    }

    @Test
    void updateGovernanceWritesScopedAuditEntry() {
        DataExtension de = dataExtension();
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(de));
        when(deRepository.save(de)).thenReturn(de);
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());

        DataExtensionDto.Response response = service.updateGovernance("de-1",
                DataExtensionDto.GovernanceMetadata.builder()
                        .sourceType("API")
                        .sourceSystem("profile-api")
                        .dataClassification("RESTRICTED")
                        .build());

        assertThat(response.getGovernance().getSourceType()).isEqualTo("API");
        assertThat(response.getGovernance().getReviewedBy()).isEqualTo("user-1");
        assertThat(response.getGovernance().getReviewedAt()).isNotNull();
        ArgumentCaptor<DataExtensionGovernanceAudit> auditCaptor = ArgumentCaptor.forClass(DataExtensionGovernanceAudit.class);
        verify(governanceAuditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("GOVERNANCE_UPDATED");
        assertThat(auditCaptor.getValue().getCreatedBy()).isEqualTo("user-1");
        assertThat(auditCaptor.getValue().getMetadata()).containsEntry("sourceType", "API");
    }

    @Test
    void markImportProvenanceUpdatesGovernanceAndWritesAuditEntry() {
        DataExtension de = dataExtension();
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(de));
        when(deRepository.save(de)).thenReturn(de);

        service.markImportProvenance("de-1", "job-1", "imports/customers.csv");

        assertThat(de.getSourceType()).isEqualTo("IMPORT");
        assertThat(de.getSourceSystem()).isEqualTo("AUDIENCE_IMPORT");
        assertThat(de.getSourceReference()).isEqualTo("job-1");
        assertThat(de.getGovernanceNotes()).contains("job-1").contains("imports/customers.csv");
        assertThat(de.getGovernanceReviewedBy()).isEqualTo("user-1");
        assertThat(de.getGovernanceReviewedAt()).isNotNull();
        ArgumentCaptor<DataExtensionGovernanceAudit> auditCaptor = ArgumentCaptor.forClass(DataExtensionGovernanceAudit.class);
        verify(governanceAuditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("IMPORT_PROVENANCE_POPULATED");
        assertThat(auditCaptor.getValue().getCreatedBy()).isEqualTo("user-1");
        assertThat(auditCaptor.getValue().getMetadata())
                .containsEntry("sourceType", "IMPORT")
                .containsEntry("sourceSystem", "AUDIENCE_IMPORT")
                .containsEntry("sourceReference", "job-1");
    }

    @Test
    void listGovernanceAuditUsesTenantWorkspaceScopedRepository() {
        DataExtension de = dataExtension();
        DataExtensionGovernanceAudit audit = new DataExtensionGovernanceAudit();
        audit.setId("audit-1");
        audit.setTenantId("tenant-1");
        audit.setWorkspaceId("workspace-1");
        audit.setDataExtensionId("de-1");
        audit.setAction("RELATIONSHIPS_UPDATED");
        audit.setSummary("Relationship metadata updated");
        audit.setMetadata(Map.of("relationshipCount", 1));
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(de));
        when(governanceAuditRepository.findTop20ByTenantIdAndWorkspaceIdAndDataExtensionIdOrderByCreatedAtDesc(
                "tenant-1", "workspace-1", "de-1")).thenReturn(List.of(audit));

        List<DataExtensionDto.GovernanceAuditResponse> response = service.listGovernanceAudit("de-1");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getAction()).isEqualTo("RELATIONSHIPS_UPDATED");
    }

    @Test
    void deleteDataExtensionSoftDeletesAndSavesScopedEntity() {
        DataExtension de = dataExtension();
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(de));

        service.deleteDataExtension("de-1");

        assertThat(de.getDeletedAt()).isNotNull();
        verify(deRepository).save(de);
        ArgumentCaptor<DataExtensionGovernanceAudit> auditCaptor = ArgumentCaptor.forClass(DataExtensionGovernanceAudit.class);
        verify(governanceAuditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("DELETED");
        verify(lifecycleAuditService).dataExtensionDeleted(de);
    }

    @Test
    void deleteDataExtensionRejectsForeignIdWithoutLifecycleAudit() {
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "foreign-de"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteDataExtension("foreign-de"))
                .isInstanceOf(NotFoundException.class);

        verify(lifecycleAuditService, never()).dataExtensionDeleted(any());
    }

    @Test
    void updateRetentionPolicyWritesLifecycleAudit() {
        DataExtension de = dataExtension();
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(de));
        when(deRepository.save(de)).thenReturn(de);
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());

        DataExtensionDto.Response response = service.updateRetentionPolicy("de-1",
                DataExtensionDto.RetentionPolicyRequest.builder()
                        .retentionAction("DELETE_RECORDS")
                        .retentionDays(90)
                        .build());

        assertThat(response.getRetentionAction()).isEqualTo("DELETE_RECORDS");
        assertThat(response.getRetentionDays()).isEqualTo(90);
        verify(lifecycleAuditService).dataExtensionRetentionUpdated(de);
    }

    @Test
    void updateRetentionPolicyValidationFailureDoesNotWriteLifecycleAudit() {
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-1"))
                .thenReturn(Optional.of(dataExtension()));

        assertThatThrownBy(() -> service.updateRetentionPolicy("de-1",
                DataExtensionDto.RetentionPolicyRequest.builder()
                        .retentionAction("DELETE_RECORDS")
                        .build()))
                .isInstanceOf(ValidationException.class);

        verify(lifecycleAuditService, never()).dataExtensionRetentionUpdated(any());
    }

    private DataExtension dataExtension() {
        DataExtension de = new DataExtension();
        de.setId("de-1");
        de.setTenantId("tenant-1");
        de.setWorkspaceId("workspace-1");
        de.setName("Customers");
        return de;
    }

    private DataExtension targetDataExtension() {
        DataExtension de = new DataExtension();
        de.setId("de-target");
        de.setTenantId("tenant-1");
        de.setWorkspaceId("workspace-1");
        de.setName("Customer Profiles");
        return de;
    }

    private List<DataExtensionField> fields() {
        DataExtensionField subscriberKey = field("subscriberKey", DataExtensionField.FieldType.TEXT, true, true);
        DataExtensionField email = field("email", DataExtensionField.FieldType.EMAIL, true, false);
        DataExtensionField score = field("score", DataExtensionField.FieldType.NUMBER, false, false);
        return List.of(subscriberKey, email, score);
    }

    private DataExtensionField field(String name, DataExtensionField.FieldType type, boolean required, boolean primaryKey) {
        DataExtensionField field = new DataExtensionField();
        field.setDataExtensionId("de-1");
        field.setFieldName(name);
        field.setFieldType(type);
        field.setRequired(required);
        field.setPrimaryKey(primaryKey);
        return field;
    }

    private DataExtensionRecord record(Map<String, Object> data) {
        DataExtensionRecord record = new DataExtensionRecord();
        record.setId("record-" + data.get("subscriberKey"));
        record.setTenantId("tenant-1");
        record.setWorkspaceId("workspace-1");
        record.setDataExtensionId("de-1");
        record.setRecordData(data);
        return record;
    }
}
