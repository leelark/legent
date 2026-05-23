package com.legent.identity.service;

import com.legent.common.constant.AppConstants;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.identity.domain.User;
import com.legent.identity.dto.UserDto;
import com.legent.identity.repository.UserRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final ExampleMatcher USER_TENANT_MATCHER = ExampleMatcher.matchingAll()
            .withIgnorePaths(
                    "id",
                    "email",
                    "passwordHash",
                    "firstName",
                    "lastName",
                    "role",
                    "externalId",
                    "identityProviderId",
                    "active",
                    "isActive",
                    "lastLoginAt",
                    "createdAt",
                    "updatedAt",
                    "createdBy",
                    "deletedAt",
                    "deleted",
                    "version");
    private static final Sort USER_LIST_SORT = Sort.by(Sort.Direction.ASC, "email");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserDto.Response> listUsers() {
        return listUsers(null);
    }

    @Transactional(readOnly = true)
    public List<UserDto.Response> listUsers(Integer limit) {
        String tenantId = TenantContext.requireTenantId();
        User probe = new User();
        probe.setTenantId(tenantId);
        return userRepository.findAll(
                        Example.of(probe, USER_TENANT_MATCHER),
                        PageRequest.of(0, boundedListLimit(limit), USER_LIST_SORT))
                .getContent()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDto.Response getUser(String id) {
        String tenantId = TenantContext.requireTenantId();
        return userRepository.findByTenantIdAndId(tenantId, id)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("User", id));
    }

    @Transactional
    public UserDto.Response createUser(UserDto.Request request) {
        String tenantId = TenantContext.requireTenantId();

        if (userRepository.existsByTenantIdAndEmailIgnoreCase(tenantId, request.getEmail())) {
            throw new ConflictException("User", "email", request.getEmail());
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required when creating a user");
        }

        User user = User.builder()
                .tenantId(tenantId)
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(request.getRole())
                .isActive(request.isActive())
                .build();

        user = userRepository.save(java.util.Objects.requireNonNull(user));
        log.info("User created: id={}, email={}, tenant={}", user.getId(), user.getEmail(), tenantId);
        return toResponse(user);
    }

    @Transactional
    public UserDto.Response updateUser(String id, UserDto.Request request) {
        String tenantId = TenantContext.requireTenantId();
        User user = userRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("User", id));

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setRole(request.getRole());
        user.setActive(request.isActive());

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        user = userRepository.save(user);
        log.info("User updated: id={}", id);
        return toResponse(user);
    }

    @Transactional
    public void deleteUser(String id) {
        String tenantId = TenantContext.requireTenantId();
        User user = userRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("User", id));

        userRepository.delete(java.util.Objects.requireNonNull(user));
        log.info("User deleted: id={}", id);
    }

    private UserDto.Response toResponse(User user) {
        return UserDto.Response.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .roles(List.of(user.getRole() == null ? "USER" : user.getRole().toUpperCase(Locale.ROOT)))
                .isActive(user.isActive())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private int boundedListLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return AppConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, AppConstants.MAX_PAGE_SIZE);
    }
}
