package com.legent.platform.service;

import com.legent.platform.domain.TenantConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FoundationSettingsBridgeService {

    private final WebClient webClient;

    @Value("${legent.foundation.base-url:http://foundation-service:8081/api/v1}")
    private String foundationBaseUrl;

    public TenantConfig loadTenantConfig(String tenantId, String workspaceId) {
        List<Map<String, Object>> settings = fetchSettings(tenantId, workspaceId);
        TenantConfig config = new TenantConfig();
        config.setTenantId(tenantId);
        config.setThemeColor(findValue(settings, "platform.theme_color", "#4F46E5"));
        config.setTimezone(findValue(settings, "platform.timezone", "UTC"));
        config.setLogoUrl(findValue(settings, "platform.logo_url", null));
        config.setFeaturesJson(findValue(settings, "platform.features_json", "{}"));
        return config;
    }

    public TenantConfig saveTenantConfig(String tenantId, String workspaceId, TenantConfig config) {
        applySetting(tenantId, workspaceId, "platform.theme_color", safe(config.getThemeColor(), "#4F46E5"), "STRING");
        applySetting(tenantId, workspaceId, "platform.timezone", safe(config.getTimezone(), "UTC"), "STRING");
        applySetting(tenantId, workspaceId, "platform.logo_url", safe(config.getLogoUrl(), ""), "STRING");
        applySetting(tenantId, workspaceId, "platform.features_json", safe(config.getFeaturesJson(), "{}"), "JSON");
        return loadTenantConfig(tenantId, workspaceId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchSettings(String tenantId, String workspaceId) {
        Map<String, Object> response = webClient.get()
                .uri(foundationBaseUrl + "/admin/settings?module=system")
                .header("X-Tenant-Id", tenantId)
                .header("X-Workspace-Id", workspaceId)
                .header("X-Request-Id", UUID.randomUUID().toString())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !(response.get("data") instanceof List<?> data)) {
            return List.of();
        }

        return data.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private void applySetting(String tenantId, String workspaceId, String key, String value, String type) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", key);
        payload.put("value", value);
        payload.put("module", "system");
        payload.put("category", "SYSTEM");
        payload.put("type", type);
        payload.put("scope", "WORKSPACE");
        payload.put("workspaceId", workspaceId);

        webClient.post()
                .uri(foundationBaseUrl + "/admin/settings/apply")
                .header("X-Tenant-Id", tenantId)
                .header("X-Workspace-Id", workspaceId)
                .header("X-Request-Id", UUID.randomUUID().toString())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    private String findValue(List<Map<String, Object>> settings, String key, String fallback) {
        for (Map<String, Object> setting : settings) {
            if (key.equals(setting.get("key"))) {
                Object value = setting.get("value");
                return value == null ? fallback : String.valueOf(value);
            }
        }
        return fallback;
    }

    private String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
