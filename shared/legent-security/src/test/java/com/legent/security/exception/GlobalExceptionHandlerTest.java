package com.legent.security.exception;

import com.legent.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void badCredentialsReturnsUnauthorized() {
        var response = handler.handleBadCredentials(new BadCredentialsException("Invalid credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ApiResponse<Void> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getError()).isNotNull();
        assertThat(body.getError().getErrorCode()).isEqualTo("UNAUTHORIZED");
    }
}
