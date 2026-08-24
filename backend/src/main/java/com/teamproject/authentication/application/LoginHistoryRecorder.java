package com.teamproject.authentication.application;

import com.teamproject.authentication.domain.LoginHistory;
import com.teamproject.authentication.domain.LoginHistoryRepository;
import com.teamproject.user.domain.User;
import org.springframework.stereotype.Component;

@Component
public class LoginHistoryRecorder {
    private final LoginHistoryRepository history;
    public LoginHistoryRecorder(LoginHistoryRepository history) { this.history = history; }

    public void success(User user, String ipAddress, String deviceName) {
        history.save(LoginHistory.success(user, ipAddress, deviceName));
    }

    public void failure(String attemptedIdentifier, String ipAddress, String deviceName) {
        history.save(LoginHistory.failure(attemptedIdentifier, ipAddress, deviceName));
    }
}
