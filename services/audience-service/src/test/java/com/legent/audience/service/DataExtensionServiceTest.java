package com.legent.audience.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.audience.domain.DataExtension;
import com.legent.audience.domain.DataExtensionField;
import com.legent.audience.domain.DataExtensionRecord;
import com.legent.audience.dto.DataExtensionDto;
import com.legent.audience.repository.DataExtensionFieldRepository;
import com.legent.audience.repository.DataExtensionRecordRepository;
import com.legent.audience.repository.DataExtensionRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataExtensionServiceTest {

    @Mock private DataExtensionRepository deRepository;
    @Mock private DataExtensionFieldRepository fieldRepository;
    @Mock private DataExtensionRecordRepository recordRepository;

    private DataExtensionService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-1");
        service = new DataExtensionService(deRepository, fieldRepository, recordRepository, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listRecordsUsesTenantScopedRepository() {
        DataExtension de = dataExtension();
        when(deRepository.findByTenantIdAndIdAndDeletedAtIsNull("tenant-1", "de-1")).thenReturn(Optional.of(de));
        when(recordRepository.findByTenantIdAndDataExtensionId(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listRecords("de-1", PageRequest.of(0, 20));

        verify(recordRepository).findByTenantIdAndDataExtensionId("tenant-1", "de-1", PageRequest.of(0, 20));
    }

    @Test
    void importMappingPreviewReportsMissingRequiredField() {
        when(deRepository.findByTenantIdAndIdAndDeletedAtIsNull("tenant-1", "de-1")).thenReturn(Optional.of(dataExtension()));
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
        when(deRepository.findByTenantIdAndIdAndDeletedAtIsNull("tenant-1", "de-1")).thenReturn(Optional.of(dataExtension()));
        when(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc("de-1")).thenReturn(fields());
        DataExtensionRecord matching = record(Map.of("subscriberKey", "abc", "email", "a@example.com", "score", 12L));
        DataExtensionRecord skipped = record(Map.of("subscriberKey", "def", "email", "b@example.com", "score", 1L));
        when(recordRepository.findByTenantIdAndDataExtensionId(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(matching, skipped)));
        when(recordRepository.countByTenantAndDataExtension("tenant-1", "de-1")).thenReturn(2L);

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

    private DataExtension dataExtension() {
        DataExtension de = new DataExtension();
        de.setId("de-1");
        de.setTenantId("tenant-1");
        de.setName("Customers");
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
        record.setDataExtensionId("de-1");
        record.setRecordData(data);
        return record;
    }
}
