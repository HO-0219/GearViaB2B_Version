package com.teamproject.payment.domain;

import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_methods")
public class PaymentMethod {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Column(nullable = false, length = 20) private String provider;
    @Column(name = "billing_key_encrypted", nullable = false, length = 1000)
    private String encryptedBillingKey;
    @Column(length = 20) private String issuerCode;
    @Column(length = 40) private String maskedNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    protected PaymentMethod() {}
    public PaymentMethod(User user, String encryptedBillingKey, String issuerCode, String maskedNumber) {
        this.user = user;
        this.provider = "TOSS";
        this.encryptedBillingKey = encryptedBillingKey;
        this.issuerCode = issuerCode;
        this.maskedNumber = maskedNumber;
        this.status = Status.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }
    public void deactivate() { status = Status.INACTIVE; updatedAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getProvider() { return provider; }
    public String getEncryptedBillingKey() { return encryptedBillingKey; }
    public String getIssuerCode() { return issuerCode; }
    public String getMaskedNumber() { return maskedNumber; }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public enum Status { ACTIVE, INACTIVE, DELETED }
}
