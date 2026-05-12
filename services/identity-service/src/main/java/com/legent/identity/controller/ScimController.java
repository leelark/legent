package com.legent.identity.controller;

import com.legent.identity.dto.FederationDto;
import com.legent.identity.service.ScimProvisioningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/scim/v2")
@RequiredArgsConstructor
public class ScimController {

    private final ScimProvisioningService scimProvisioningService;

    @GetMapping("/ServiceProviderConfig")
    public Map<String, Object> serviceProviderConfig() {
        return scimProvisioningService.serviceProviderConfig();
    }

    @GetMapping("/ResourceTypes")
    public Map<String, Object> resourceTypes() {
        return scimProvisioningService.resourceTypes();
    }

    @GetMapping("/Schemas")
    public Map<String, Object> schemas() {
        return scimProvisioningService.schemas();
    }

    @GetMapping("/Users")
    public Map<String, Object> listUsers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "100") int count) {
        return scimProvisioningService.listUsers(authorization, filter, startIndex, count);
    }

    @PostMapping("/Users")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody FederationDto.ScimUserRequest request) {
        return scimProvisioningService.createUser(authorization, request);
    }

    @GetMapping("/Users/{id}")
    public Map<String, Object> getUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id) {
        return scimProvisioningService.getUser(authorization, id);
    }

    @PutMapping("/Users/{id}")
    public Map<String, Object> replaceUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id,
            @Valid @RequestBody FederationDto.ScimUserRequest request) {
        return scimProvisioningService.replaceUser(authorization, id, request);
    }

    @PatchMapping("/Users/{id}")
    public Map<String, Object> patchUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id,
            @RequestBody FederationDto.ScimPatchRequest request) {
        return scimProvisioningService.patchUser(authorization, id, request);
    }

    @DeleteMapping("/Users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id) {
        scimProvisioningService.deleteUser(authorization, id);
    }

    @GetMapping("/Groups")
    public Map<String, Object> listGroups(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "100") int count) {
        return scimProvisioningService.listGroups(authorization, startIndex, count);
    }

    @PostMapping("/Groups")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createGroup(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody FederationDto.ScimGroupRequest request) {
        return scimProvisioningService.createGroup(authorization, request);
    }

    @GetMapping("/Groups/{id}")
    public Map<String, Object> getGroup(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id) {
        return scimProvisioningService.getGroup(authorization, id);
    }

    @PutMapping("/Groups/{id}")
    public Map<String, Object> replaceGroup(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id,
            @Valid @RequestBody FederationDto.ScimGroupRequest request) {
        return scimProvisioningService.replaceGroup(authorization, id, request);
    }

    @DeleteMapping("/Groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id) {
        scimProvisioningService.deleteGroup(authorization, id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleScimError(IllegalArgumentException error) {
        String message = error.getMessage() == null ? "SCIM request failed" : error.getMessage();
        HttpStatus status = message.contains("bearer token") || message.startsWith("Invalid SCIM") || message.contains("lacks scope")
                ? HttpStatus.UNAUTHORIZED
                : message.contains("not found")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of(
                "schemas", List.of("urn:ietf:params:scim:api:messages:2.0:Error"),
                "status", String.valueOf(status.value()),
                "detail", message
        ));
    }
}
