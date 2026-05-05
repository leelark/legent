package com.legent.identity.repository;

import com.legent.identity.domain.AccountRoleBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountRoleBindingRepository extends JpaRepository<AccountRoleBinding, String> {
    List<AccountRoleBinding> findByMembershipId(String membershipId);
}
