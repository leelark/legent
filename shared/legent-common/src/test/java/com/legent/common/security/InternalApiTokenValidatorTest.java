package com.legent.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalApiTokenValidatorTest {

    @Test
    void rejectsDocumentedPlaceholderToken() {
        assertThatThrownBy(() -> InternalApiTokenValidator.requireConfigured(
                "legent.internal.api-token",
                "replace_with_32_plus_character_internal_api_token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-placeholder");
    }

    @Test
    void acceptsLongNonPlaceholderTokenAndComparesConstantTime() {
        String token = "internal-service-token-1234567890abcdef";

        assertThat(InternalApiTokenValidator.requireConfigured("legent.internal.api-token", token))
                .isEqualTo(token);
        assertThat(InternalApiTokenValidator.matches(token, token)).isTrue();
        assertThat(InternalApiTokenValidator.matches(token, "internal-service-token-abcdef1234567890")).isFalse();
    }
}
