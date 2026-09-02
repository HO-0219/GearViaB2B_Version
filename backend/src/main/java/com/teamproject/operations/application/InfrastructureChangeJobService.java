package com.teamproject.operations.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.operations.domain.InfrastructureChangeJob;
import com.teamproject.operations.domain.InfrastructureChangeJob.Status;
import com.teamproject.operations.domain.InfrastructureChangeJob.Type;
import com.teamproject.operations.domain.InfrastructureChangeJobRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Service
public class InfrastructureChangeJobService {
    private static final Map<Status, Set<Status>> ALLOWED_TRANSITIONS = allowedTransitions();

    private final InfrastructureChangeJobRepository jobs;
    private final UserRepository users;

    public InfrastructureChangeJobService(InfrastructureChangeJobRepository jobs, UserRepository users) {
        this.jobs = jobs;
        this.users = users;
    }

    @Transactional
    public JobSnapshot create(Type type, Long actorId, String redactedTarget, long estimatedSeconds,
            String correlationId) {
        requireCreateInput(type, actorId, redactedTarget, estimatedSeconds, correlationId);
        if (jobs.existsByCorrelationId(correlationId.trim())) {
            throw conflict("INFRASTRUCTURE_CHANGE_CORRELATION_CONFLICT", "이미 등록된 운영 변경 요청입니다.");
        }
        User actor = users.findById(actorId)
                .orElseThrow(() -> notFound("INFRASTRUCTURE_CHANGE_ACTOR_NOT_FOUND", "요청 사용자를 찾을 수 없습니다."));
        InfrastructureChangeJob job = new InfrastructureChangeJob(type, actor, redactedTarget.trim(),
                estimatedSeconds, correlationId.trim());
        return snapshot(jobs.saveAndFlush(job));
    }

    @Transactional
    public JobSnapshot transition(Long jobId, long expectedVersion, Status targetStatus, int progressPercent,
            String summary) {
        InfrastructureChangeJob job = jobs.findById(jobId)
                .orElseThrow(() -> notFound("INFRASTRUCTURE_CHANGE_NOT_FOUND", "운영 변경 작업을 찾을 수 없습니다."));
        if (job.getVersion() != expectedVersion) {
            throw conflict("INFRASTRUCTURE_CHANGE_VERSION_CONFLICT", "다른 관리자가 먼저 변경했습니다. 새로고침 후 다시 시도해 주세요.");
        }
        if (targetStatus == null || !ALLOWED_TRANSITIONS.getOrDefault(job.getStatus(), Set.of()).contains(targetStatus)) {
            throw conflict("INFRASTRUCTURE_CHANGE_TRANSITION_INVALID", "현재 단계에서 요청한 운영 변경 단계로 이동할 수 없습니다.");
        }
        if (progressPercent < 0 || progressPercent > 100) {
            throw invalid("INFRASTRUCTURE_CHANGE_PROGRESS_INVALID", "진행률은 0부터 100 사이여야 합니다.");
        }
        job.transitionTo(targetStatus, progressPercent, summary);
        return snapshot(jobs.saveAndFlush(job));
    }

    private void requireCreateInput(Type type, Long actorId, String redactedTarget, long estimatedSeconds,
            String correlationId) {
        if (type == null || actorId == null || redactedTarget == null || redactedTarget.isBlank()
                || redactedTarget.length() > 500 || estimatedSeconds < 1 || correlationId == null
                || correlationId.isBlank() || correlationId.length() > 80) {
            throw invalid("INFRASTRUCTURE_CHANGE_INPUT_INVALID", "운영 변경 요청 값이 올바르지 않습니다.");
        }
    }

    private JobSnapshot snapshot(InfrastructureChangeJob job) {
        return new JobSnapshot(job.getId(), job.getType(), job.getStatus(), job.getActor().getId(),
                job.getRedactedTarget(), job.getEstimatedSeconds(), job.getProgressPercent(),
                job.getVerificationSummary(), job.getFailureCode(), job.getRollbackSummary(),
                job.getCorrelationId(), job.getVersion(), job.getCreatedAt(), job.getUpdatedAt(),
                job.getStartedAt(), job.getCompletedAt());
    }

    private static Map<Status, Set<Status>> allowedTransitions() {
        EnumMap<Status, Set<Status>> transitions = new EnumMap<>(Status.class);
        transitions.put(Status.DRAFT, Set.of(Status.TESTING));
        transitions.put(Status.TESTING, Set.of(Status.TEST_SUCCEEDED, Status.FAILED));
        transitions.put(Status.TEST_SUCCEEDED, Set.of(Status.SCHEDULED, Status.FAILED));
        transitions.put(Status.SCHEDULED, Set.of(Status.NOTIFYING, Status.FAILED));
        transitions.put(Status.NOTIFYING, Set.of(Status.MAINTENANCE, Status.FAILED));
        transitions.put(Status.MAINTENANCE, Set.of(Status.MIGRATING, Status.FAILED));
        transitions.put(Status.MIGRATING, Set.of(Status.VERIFYING, Status.FAILED));
        transitions.put(Status.VERIFYING, Set.of(Status.SWITCHED, Status.FAILED));
        transitions.put(Status.SWITCHED, Set.of(Status.COMPLETED, Status.FAILED));
        transitions.put(Status.FAILED, Set.of(Status.ROLLING_BACK));
        transitions.put(Status.ROLLING_BACK, Set.of(Status.ROLLED_BACK, Status.FAILED));
        return Map.copyOf(transitions);
    }

    private ApplicationException notFound(String code, String message) {
        return new ApplicationException(code, HttpStatus.NOT_FOUND, message);
    }

    private ApplicationException conflict(String code, String message) {
        return new ApplicationException(code, HttpStatus.CONFLICT, message);
    }

    private ApplicationException invalid(String code, String message) {
        return new ApplicationException(code, HttpStatus.BAD_REQUEST, message);
    }

    public record JobSnapshot(Long id, Type type, Status status, Long actorId, String redactedTarget,
            long estimatedSeconds, int progressPercent, String verificationSummary, String failureCode,
            String rollbackSummary, String correlationId, long version, LocalDateTime createdAt,
            LocalDateTime updatedAt, LocalDateTime startedAt, LocalDateTime completedAt) {}
}
