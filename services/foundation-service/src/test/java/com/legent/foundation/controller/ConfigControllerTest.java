package com.legent.foundation.controller;


import com.legent.foundation.service.ConfigService;
import com.legent.foundation.dto.ConfigDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConfigController.class)
@DisplayName("ConfigController API Tests")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("null")
class ConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ConfigService configService;

    @Test
    @DisplayName("GET /api/v1/configs/resolve/{key} returns config value")
    void resolveConfig_returnsOk() throws Exception {
        ConfigDto.Response response = ConfigDto.Response.builder()
                .configKey("smtp.provider")
                .configValue("postal")
                .category("DELIVERY")
                .build();

        when(configService.resolveConfig("smtp.provider")).thenReturn(response);

        mockMvc.perform(get("/api/v1/configs/resolve/smtp.provider")
                        .header("X-Tenant-Id", "test-tenant")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.configKey").value("smtp.provider"))
                .andExpect(jsonPath("$.data.configValue").value("postal"));
    }

    @Test
    @DisplayName("POST /api/v1/configs validates request body")
    void createConfig_validatesRequest() throws Exception {
        mockMvc.perform(post("/api/v1/configs")
                        .header("X-Tenant-Id", "test-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
