package com.teamproject.user.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.user.application.dto.UserProfileDtos.ProfileResponse;
import com.teamproject.user.application.dto.UserProfileDtos.UpdateProfileRequest;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import com.teamproject.common.storage.ImageStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserProfileService {
    private final UserRepository users;
    private final ImageStorageService images;

    public UserProfileService(UserRepository users, ImageStorageService images) {
        this.users = users;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(Long userId) { return response(find(userId)); }

    @Transactional
    public ProfileResponse update(Long userId, UpdateProfileRequest request) {
        User user = find(userId);
        user.updateProfile(request.nickname().trim(), blankToNull(request.phoneNumber()), blankToNull(request.profileImageUrl()));
        return response(user);
    }

    @Transactional
    public ProfileResponse uploadImage(Long userId, MultipartFile file) {
        User user = find(userId);
        String previous = user.getProfileImageUrl();
        user.updateProfile(user.getNickname(), user.getPhoneNumber(), images.store(file, "profiles"));
        users.flush();
        images.deleteManagedAfterCommit(previous);
        return response(user);
    }

    private User find(Long userId) {
        return users.findById(userId).orElseThrow(() ->
                new ApplicationException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ProfileResponse response(User user) {
        return new ProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getName(),
                user.getNickname(), user.getPhoneNumber(), user.getProfileImageUrl(), user.getStatus().name(),
                user.getSystemRole().name(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
