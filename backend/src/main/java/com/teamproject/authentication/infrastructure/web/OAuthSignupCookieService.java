package com.teamproject.authentication.infrastructure.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class OAuthSignupCookieService {
    public static final String NAME = "totaskflow_oauth_signup";
    private static final long MAX_AGE_SECONDS = 600;
    private final boolean secure;

    public OAuthSignupCookieService(@Value("${app.jwt.secure-cookie}") boolean secure) {
        this.secure = secure;
    }

    public void add(HttpServletResponse response, String value) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(value, MAX_AGE_SECONDS).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0).toString());
    }

    private ResponseCookie cookie(String value, long age) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true).secure(secure).sameSite("Lax")
                .path("/api/v1/auth/oauth-signup").maxAge(age).build();
    }
}
