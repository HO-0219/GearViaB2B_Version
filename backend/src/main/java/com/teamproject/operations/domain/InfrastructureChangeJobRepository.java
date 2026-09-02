package com.teamproject.operations.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InfrastructureChangeJobRepository extends JpaRepository<InfrastructureChangeJob, Long> {
    boolean existsByCorrelationId(String correlationId);
}
