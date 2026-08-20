package com.teamproject.payment.domain;

import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_method_id") private PaymentMethod method;
    @Column(name = "subscription_id") private Long subscriptionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private OperationType operationType;
    @Column(nullable = false, length = 100, unique = true) private String idempotencyKey;
    @Column(length = 160, unique = true) private String businessKey;
    private LocalDateTime billingPeriodStart;
    @Column(length = 20) private String billingKind;
    @Column(length = 64) private String orderId;
    @Column(length = 200) private String providerPaymentKey;
    private Long amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    private Integer httpStatus;
    @Column(length = 100) private String providerCode;
    @Column(length = 500) private String providerMessage;
    @Column(nullable = false) private int retryCount;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    protected PaymentAttempt() {}
    public PaymentAttempt(User user, PaymentMethod method, OperationType type, String key, String orderId, Long amount) {
        this(user, method, null, type, key, null, null, null, orderId, amount);
    }
    public PaymentAttempt(User user, PaymentMethod method, Long subscriptionId, OperationType type,
            String key, String businessKey, LocalDateTime billingPeriodStart, String billingKind,
            String orderId, Long amount) {
        this.user = user; this.method = method; this.operationType = type; this.idempotencyKey = key;
        this.subscriptionId = subscriptionId; this.businessKey = businessKey;
        this.billingPeriodStart = billingPeriodStart;
        this.billingKind = billingKind;
        this.orderId = orderId; this.amount = amount; this.status = Status.PENDING;
        this.createdAt = LocalDateTime.now(); this.updatedAt = createdAt;
    }
    public void success(int status) { this.status = Status.SUCCESS; this.httpStatus = status; touch(); }
    public void success(int status, String providerPaymentKey) {
        this.providerPaymentKey = safe(providerPaymentKey, 200);
        success(status);
    }
    public void fail(Integer status, String code, String message) {
        this.status = Status.FAILED; this.httpStatus = status; this.providerCode = safe(code, 100);
        this.providerMessage = safe(message, 500); touch();
    }
    public void unknown(Integer status, String code, String message) {
        this.status = Status.UNKNOWN; this.httpStatus = status; this.providerCode = safe(code, 100);
        this.providerMessage = safe(message, 500); touch();
    }
    public void retrying() { this.status = Status.PENDING; this.retryCount++; touch(); }
    public void attachMethod(PaymentMethod method) { this.method = method; touch(); }
    private void touch() { this.updatedAt = LocalDateTime.now(); }
    private String safe(String value, int max) { return value == null ? null : value.substring(0, Math.min(value.length(), max)); }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public PaymentMethod getMethod() { return method; }
    public Long getSubscriptionId() { return subscriptionId; }
    public OperationType getOperationType() { return operationType; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getBusinessKey() { return businessKey; }
    public LocalDateTime getBillingPeriodStart() { return billingPeriodStart; }
    public String getBillingKind() { return billingKind; }
    public String getOrderId() { return orderId; }
    public String getProviderPaymentKey() { return providerPaymentKey; }
    public Long getAmount() { return amount; }
    public Status getStatus() { return status; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getProviderCode() { return providerCode; }
    public String getProviderMessage() { return providerMessage; }
    public int getRetryCount() { return retryCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public enum OperationType { BILLING_KEY_ISSUE, TEST_CHARGE, SUBSCRIPTION_CHARGE }
    public enum Status { PENDING, SUCCESS, FAILED, UNKNOWN }
}
