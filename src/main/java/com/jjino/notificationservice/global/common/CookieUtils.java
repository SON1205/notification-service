package com.jjino.notificationservice.global.common;

import static com.jjino.notificationservice.global.common.Constants.COOKIE_ACCESS_TOKEN;

import org.springframework.http.ResponseCookie;

public final class CookieUtils {

    private CookieUtils() {
    }

    public static ResponseCookie createAccessTokenCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(COOKIE_ACCESS_TOKEN, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api")
                .maxAge(maxAgeSeconds)
                .build();
    }

    public static ResponseCookie deleteAccessTokenCookie() {
        return ResponseCookie.from(COOKIE_ACCESS_TOKEN, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api")
                .maxAge(0)
                .build();
    }
}
