package com.example.consensus.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByApiKey(String apiKey);
    boolean existsByName(String name);
}
