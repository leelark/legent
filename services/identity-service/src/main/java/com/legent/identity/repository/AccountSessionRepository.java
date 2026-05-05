package com.legent.identity.repository;

import com.legent.identity.domain.AccountSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountSessionRepository extends JpaRepository<AccountSession, String> {
    List<AccountSession> findByAccountIdAndTenantIdOrderByLastActiveAtDesc(String accountId, String tenantId);
}
