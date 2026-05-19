package com.legent.audience.service;

import com.legent.audience.domain.DataExtension;
import com.legent.audience.domain.DataExtensionField;
import com.legent.audience.domain.DataExtensionRecord;
import com.legent.audience.dto.DataExtensionDto;
import com.legent.audience.repository.DataExtensionFieldRepository;
import com.legent.audience.repository.DataExtensionRecordRepository;
import com.legent.audience.repository.DataExtensionRepository;
import com.legent.common.exception.ValidationException;
import com.legent.security.TenantContext;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataExtensionQueryActivityServiceTest {

    @Mock private DataExtensionRepository deRepository;
    @Mock private DataExtensionFieldRepository fieldRepository;
    @Mock private DataExtensionRecordRepository recordRepository;

    private DataExtensionQueryActivityService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        service = new DataExtensionQueryActivityService(deRepository, fieldRepository, recordRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void dryRunSelectFiltersProjectsAndDoesNotWrite() {
        DataExtension source = dataExtension("de-source", "Customers", "subscriberKey");
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "Customers"))
                .thenReturn(Optional.empty());
        when(deRepository.findByTenantIdAndWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull("tenant-1", "workspace-1", "Customers"))
                .thenReturn(Optional.of(source));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-source")).thenReturn(fields("de-source"));
        when(recordRepository.findByTenantIdAndWorkspaceIdAndDataExtensionId("tenant-1", "workspace-1", "de-source", PageRequest.of(0, 500)))
                .thenReturn(new PageImpl<>(List.of(
                        record("de-source", Map.of("subscriberKey", "a", "email", "a@example.com", "score", 12L)),
                        record("de-source", Map.of("subscriberKey", "b", "email", "b@example.com", "score", 2L))
                ), PageRequest.of(0, 500), 2));

        DataExtensionDto.SqlQueryActivityResponse response = service.execute(DataExtensionDto.SqlQueryActivityRequest.builder()
                .sql("SELECT email, score FROM Customers WHERE score >= 10 ORDER BY score DESC LIMIT 10")
                .dryRun(true)
                .build());

        assertThat(response.isValid()).isTrue();
        assertThat(response.getRowsRead()).isEqualTo(2);
        assertThat(response.getRowsWritten()).isZero();
        assertThat(response.getPreviewRows()).containsExactly(Map.of("email", "a@example.com", "score", 12L));
        verify(recordRepository, never()).saveAll(any());
    }

    @Test
    void overwriteWritesNormalizedRowsToTarget() {
        DataExtension source = dataExtension("de-source", "Customers", "subscriberKey");
        DataExtension target = dataExtension("de-target", "HighValue", "subscriberKey");
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-source"))
                .thenReturn(Optional.of(source));
        when(deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "de-target"))
                .thenReturn(Optional.of(target));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-source")).thenReturn(fields("de-source"));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-target")).thenReturn(fields("de-target"));
        when(recordRepository.findByTenantIdAndWorkspaceIdAndDataExtensionId("tenant-1", "workspace-1", "de-source", PageRequest.of(0, 500)))
                .thenReturn(new PageImpl<>(List.of(record("de-source", Map.of(
                        "subscriberKey", "A",
                        "email", "A@EXAMPLE.COM",
                        "score", 12L))), PageRequest.of(0, 500), 1));
        when(recordRepository.countByTenantWorkspaceAndDataExtension("tenant-1", "workspace-1", "de-target")).thenReturn(1L);

        DataExtensionDto.SqlQueryActivityResponse response = service.execute(DataExtensionDto.SqlQueryActivityRequest.builder()
                .sql("SELECT subscriberKey, email, score FROM de-source WHERE score >= 10")
                .targetDataExtensionId("de-target")
                .writeMode("OVERWRITE")
                .dryRun(false)
                .build());

        assertThat(response.getRowsWritten()).isEqualTo(1);
        verify(recordRepository).deleteByTenantIdAndWorkspaceIdAndDataExtensionId("tenant-1", "workspace-1", "de-target");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DataExtensionRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(recordRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getRecordData()).containsEntry("email", "a@example.com");
        verify(deRepository).save(target);
    }

    @Test
    void unsafeSqlTokenIsRejected() {
        assertThatThrownBy(() -> service.execute(DataExtensionDto.SqlQueryActivityRequest.builder()
                .sql("DELETE FROM Customers")
                .dryRun(true)
                .build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("forbidden token");
    }

    private DataExtension dataExtension(String id, String name, String primaryKeyField) {
        DataExtension de = new DataExtension();
        de.setId(id);
        de.setTenantId("tenant-1");
        de.setWorkspaceId("workspace-1");
        de.setName(name);
        de.setPrimaryKeyField(primaryKeyField);
        return de;
    }

    private List<DataExtensionField> fields(String dataExtensionId) {
        return List.of(
                field(dataExtensionId, "subscriberKey", DataExtensionField.FieldType.TEXT, true, true),
                field(dataExtensionId, "email", DataExtensionField.FieldType.EMAIL, true, false),
                field(dataExtensionId, "score", DataExtensionField.FieldType.NUMBER, false, false)
        );
    }

    private DataExtensionField field(String dataExtensionId,
                                     String name,
                                     DataExtensionField.FieldType type,
                                     boolean required,
                                     boolean primaryKey) {
        DataExtensionField field = new DataExtensionField();
        field.setDataExtensionId(dataExtensionId);
        field.setFieldName(name);
        field.setFieldType(type);
        field.setRequired(required);
        field.setPrimaryKey(primaryKey);
        return field;
    }

    private DataExtensionRecord record(String dataExtensionId, Map<String, Object> data) {
        DataExtensionRecord record = new DataExtensionRecord();
        record.setId("record-" + data.get("subscriberKey"));
        record.setTenantId("tenant-1");
        record.setWorkspaceId("workspace-1");
        record.setDataExtensionId(dataExtensionId);
        record.setRecordData(data);
        return record;
    }
}
