package com.legent.identity.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityJsonbMappingTest {

    @Test
    void jsonbStringFieldsUseHibernateJsonBinding() throws NoSuchFieldException {
        assertJsonBinding(OnboardingState.class, "payload");
        assertJsonBinding(PasswordResetToken.class, "metadata");
        assertJsonBinding(UserPreference.class, "metadata");
    }

    private void assertJsonBinding(Class<?> type, String fieldName) throws NoSuchFieldException {
        JdbcTypeCode annotation = type.getDeclaredField(fieldName).getAnnotation(JdbcTypeCode.class);

        assertThat(annotation)
                .as("%s.%s should bind as JSON for PostgreSQL jsonb columns", type.getSimpleName(), fieldName)
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(SqlTypes.JSON);
    }
}
