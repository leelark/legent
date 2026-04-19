package com.legent.audience.service;

import java.util.List;

import java.util.Map;

import com.legent.audience.domain.*;
import com.legent.audience.dto.DataExtensionDto;
import com.legent.audience.repository.*;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class DataExtensionService {

    private final DataExtensionRepository deRepository;
    private final DataExtensionFieldRepository fieldRepository;
    private final DataExtensionRecordRepository recordRepository;

    @Transactional(readOnly = true)
    public Page<DataExtensionDto.Response> list(Pageable pageable) {
        String tenantId = TenantContext.getTenantId();
        return deRepository.findAllByTenant(tenantId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DataExtensionDto.Response getById(String id) {
        String tenantId = TenantContext.getTenantId();
        DataExtension de = deRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
                .orElseThrow(() -> new NotFoundException("DataExtension", id));
        return toResponse(de);
    }

    @Transactional
    public DataExtensionDto.Response create(DataExtensionDto.CreateRequest request) {
        String tenantId = TenantContext.getTenantId();
        if (deRepository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, request.getName())) {
            throw new ConflictException("DataExtension", "name", request.getName());
        }

        DataExtension de = new DataExtension();
        de.setTenantId(tenantId);
        de.setName(request.getName());
        de.setDescription(request.getDescription());
        de.setSendable(request.isSendable());
        de.setSendableField(request.getSendableField());
        de.setPrimaryKeyField(request.getPrimaryKeyField());
        DataExtension savedDe = deRepository.save(de);

        for (DataExtensionDto.FieldDefinition fd : request.getFields()) {
            DataExtensionField field = new DataExtensionField();
            field.setDataExtensionId(savedDe.getId());
            field.setFieldName(fd.getFieldName());
            field.setFieldType(fd.getFieldType() != null
                    ? DataExtensionField.FieldType.valueOf(fd.getFieldType().toUpperCase())
                    : DataExtensionField.FieldType.TEXT);
            field.setRequired(fd.isRequired());
            field.setPrimaryKey(fd.isPrimaryKey());
            field.setDefaultValue(fd.getDefaultValue());
            field.setMaxLength(fd.getMaxLength());
            field.setOrdinal(fd.getOrdinal());
            fieldRepository.save(field);
        }

        log.info("Data extension created: name={}, id={}, fields={}", de.getName(), savedDe.getId(), request.getFields().size());
        return toResponse(savedDe);
    }

    @Transactional
    public DataExtensionDto.RecordResponse addRecord(String deId, DataExtensionDto.RecordRequest request) {
        String tenantId = TenantContext.getTenantId();
        DataExtension de = deRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, deId)
                .orElseThrow(() -> new NotFoundException("DataExtension", deId));

        validateRecord(de.getId(), request.getData());

        DataExtensionRecord record = new DataExtensionRecord();
        record.setTenantId(tenantId);
        record.setDataExtensionId(deId);
        record.setRecordData(request.getData());
        DataExtensionRecord saved = recordRepository.save(record);

        de.setRecordCount(recordRepository.countByDataExtension(deId));
        deRepository.save(de);

        return DataExtensionDto.RecordResponse.builder()
                .id(saved.getId()).dataExtensionId(deId)
                .data(saved.getRecordData())
                .createdAt(saved.getCreatedAt()).updatedAt(saved.getUpdatedAt()).build();
    }

    @Transactional(readOnly = true)
    public Page<DataExtensionDto.RecordResponse> listRecords(String deId, Pageable pageable) {
        return recordRepository.findByDataExtensionId(deId, pageable)
                .map(r -> DataExtensionDto.RecordResponse.builder()
                        .id(r.getId()).dataExtensionId(r.getDataExtensionId())
                        .data(r.getRecordData())
                        .createdAt(r.getCreatedAt()).updatedAt(r.getUpdatedAt()).build());
    }

    @Transactional
    public void deleteDataExtension(String id) {
        String tenantId = TenantContext.getTenantId();
        DataExtension de = deRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
                .orElseThrow(() -> new NotFoundException("DataExtension", id));
        de.softDelete();
        deRepository.save(de);
        log.info("Data extension deleted: id={}", id);
    }

    @Transactional(readOnly = true)
    public long count() {
        return deRepository.countByTenant(TenantContext.getTenantId());
    }

    private void validateRecord(String deId, Map<String, Object> data) {
        List<DataExtensionField> fields = fieldRepository.findByDataExtensionIdOrderByOrdinalAsc(deId);
        for (DataExtensionField field : fields) {
            if (field.isRequired() && !data.containsKey(field.getFieldName())) {
                throw new ValidationException("Missing required field: " + field.getFieldName());
            }
        }
    }

    private DataExtensionDto.Response toResponse(DataExtension de) {
        List<DataExtensionField> fields = fieldRepository.findByDataExtensionIdOrderByOrdinalAsc(de.getId());
        List<DataExtensionDto.FieldDefinition> fieldDtos = fields.stream()
                .map(f -> DataExtensionDto.FieldDefinition.builder()
                        .fieldName(f.getFieldName()).fieldType(f.getFieldType().name())
                        .required(f.isRequired()).primaryKey(f.isPrimaryKey())
                        .defaultValue(f.getDefaultValue()).maxLength(f.getMaxLength())
                        .ordinal(f.getOrdinal()).build())
                .toList();

        return DataExtensionDto.Response.builder()
                .id(de.getId()).name(de.getName()).description(de.getDescription())
                .sendable(de.isSendable()).sendableField(de.getSendableField())
                .primaryKeyField(de.getPrimaryKeyField()).recordCount(de.getRecordCount())
                .fields(fieldDtos)
                .createdAt(de.getCreatedAt()).updatedAt(de.getUpdatedAt()).build();
    }
}
