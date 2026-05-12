package com.legent.foundation.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorePlatformRepositoryTest {

    @Test
    void safeTable_allowsOnlyKnownDynamicTables() {
        assertThat(CorePlatformRepository.safeTable("global_operating_models"))
                .isEqualTo("global_operating_models");

        assertThatThrownBy(() -> CorePlatformRepository.safeTable("global_operating_models; drop table users"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsafe SQL identifier");

        assertThatThrownBy(() -> CorePlatformRepository.safeTable("unknown_table"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported dynamic SQL table");
    }

    @Test
    void safeKeyColumn_allowsOnlyKnownLookupColumns() {
        assertThat(CorePlatformRepository.safeKeyColumn("policy_key")).isEqualTo("policy_key");

        assertThatThrownBy(() -> CorePlatformRepository.safeKeyColumn("policy_key OR 1=1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsafe SQL identifier");

        assertThatThrownBy(() -> CorePlatformRepository.safeKeyColumn("created_at"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported dynamic SQL key column");
    }

    @Test
    void safeOrderBy_rejectsInjectedOrderClauses() {
        assertThat(CorePlatformRepository.safeOrderBy("data_class ASC, created_at DESC"))
                .isEqualTo("data_class ASC, created_at DESC");

        assertThat(CorePlatformRepository.safeOrderBy("tenant_id NULLS FIRST, role_key ASC"))
                .isEqualTo("tenant_id NULLS FIRST, role_key ASC");

        assertThatThrownBy(() -> CorePlatformRepository.safeOrderBy("created_at DESC; drop table users"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsafe");
    }
}
