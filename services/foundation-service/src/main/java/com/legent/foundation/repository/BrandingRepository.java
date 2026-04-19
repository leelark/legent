package com.legent.foundation.repository;

import com.legent.foundation.domain.Branding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BrandingRepository extends JpaRepository<Branding, Long> {
}
