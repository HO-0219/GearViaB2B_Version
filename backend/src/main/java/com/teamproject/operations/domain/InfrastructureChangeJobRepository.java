package com.teamproject.operations.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface InfrastructureChangeJobRepository extends JpaRepository<InfrastructureChangeJob, Long> {
    boolean existsByCorrelationId(String correlationId);

    List<InfrastructureChangeJob> findByTypeAndStatus(
            InfrastructureChangeJob.Type type, InfrastructureChangeJob.Status status);

    List<InfrastructureChangeJob> findByTypeAndStatusIn(
            InfrastructureChangeJob.Type type, Collection<InfrastructureChangeJob.Status> statuses);
}
