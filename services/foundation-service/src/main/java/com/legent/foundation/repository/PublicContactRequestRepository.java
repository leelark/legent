package com.legent.foundation.repository;

import com.legent.foundation.domain.PublicContactRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicContactRequestRepository extends JpaRepository<PublicContactRequest, String> {
    Page<PublicContactRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<PublicContactRequest> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
}
