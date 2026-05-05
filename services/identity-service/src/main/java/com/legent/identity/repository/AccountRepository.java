package com.legent.identity.repository;

import com.legent.identity.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findByEmailIgnoreCase(String email);
}
