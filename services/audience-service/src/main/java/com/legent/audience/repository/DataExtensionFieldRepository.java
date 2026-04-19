package com.legent.audience.repository;

import java.util.List;

import com.legent.audience.domain.DataExtensionField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface DataExtensionFieldRepository extends JpaRepository<DataExtensionField, String> {

    List<DataExtensionField> findByDataExtensionIdOrderByOrdinalAsc(String dataExtensionId);

    void deleteByDataExtensionId(String dataExtensionId);
}
