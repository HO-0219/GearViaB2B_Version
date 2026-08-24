package com.teamproject.authentication.domain;

import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One row per login attempt, success or failure, so admins can review a user's sign-in history. */
@Entity
@Table(name = "login_history")
public class LoginHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String username;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Outcome outcome;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(name = "device_name", length = 120)
    private String deviceName;
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected LoginHistory() {}

    private LoginHistory(String username, User user, Outcome outcome, String ipAddress, String deviceName) {
        this.username = username;
        this.user = user;
        this.outcome = outcome;
        this.ipAddress = ipAddress;
        this.deviceName = deviceName;
        this.occurredAt = LocalDateTime.now();
    }

    public static LoginHistory success(User user, String ipAddress, String deviceName) {
        return new LoginHistory(user.getUsername(), user, Outcome.SUCCESS, ipAddress, deviceName);
    }

    public static LoginHistory failure(String attemptedIdentifier, String ipAddress, String deviceName) {
        return new LoginHistory(attemptedIdentifier, null, Outcome.FAILURE, ipAddress, deviceName);
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public User getUser() { return user; }
    public Outcome getOutcome() { return outcome; }
    public String getIpAddress() { return ipAddress; }
    public String getDeviceName() { return deviceName; }
    public LocalDateTime getOccurredAt() { return occurredAt; }

    public enum Outcome { SUCCESS, FAILURE }
}
