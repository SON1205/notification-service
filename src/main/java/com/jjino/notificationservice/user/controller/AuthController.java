package com.jjino.notificationservice.user.controller;

import static org.springframework.http.HttpHeaders.SET_COOKIE;

import com.jjino.notificationservice.global.auth.JwtTokenProvider;
import com.jjino.notificationservice.global.common.CookieUtils;
import com.jjino.notificationservice.user.controller.dto.LoginRequest;
import com.jjino.notificationservice.user.controller.dto.LoginResponse;
import com.jjino.notificationservice.user.controller.dto.SignupRequest;
import com.jjino.notificationservice.user.controller.dto.SignupResponse;
import com.jjino.notificationservice.user.service.AuthService;
import com.jjino.notificationservice.user.service.dto.LoginCommand;
import com.jjino.notificationservice.user.service.dto.SignupCommand;
import com.jjino.notificationservice.user.service.dto.SignupInfo;
import com.jjino.notificationservice.user.service.dto.TokenInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupInfo info = authService.signup(new SignupCommand(request.username(), request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new SignupResponse(info.userId(), info.username()));
    }

    /**
     * 하이브리드 로그인: Cookie(웹) + body token(앱) 동시 제공.
     * - 웹 클라이언트: HttpOnly Cookie를 자동 전송하여 인증. body token은 무시해야 함.
     * - 앱 클라이언트: body token을 SecureStorage에 저장, Authorization 헤더로 전송.
     * body에 토큰을 포함하더라도 HttpOnly Cookie는 JS에서 접근 불가하므로,
     * 웹에서 localStorage에 저장하지 않는 한 XSS 위험은 제한적.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenInfo tokenInfo = authService.login(new LoginCommand(request.username(), request.password()));

        ResponseCookie cookie = CookieUtils.createAccessTokenCookie(
                tokenInfo.token(), jwtTokenProvider.getExpirationSeconds()
        );

        return ResponseEntity.ok()
                .header(SET_COOKIE, cookie.toString())
                .body(new LoginResponse(tokenInfo.token()));
    }

    /**
     * 쿠키 기반 로그아웃. 브라우저의 HttpOnly Cookie를 삭제한다.
     * 현재는 stateless JWT이므로 서버 사이드 토큰 무효화는 없음.
     * 앱 클라이언트는 로컬 토큰을 직접 삭제해야 함.
     * 서버 사이드 무효화가 필요하면 Refresh Token + Redis 블랙리스트 도입 필요 (Phase 3).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = CookieUtils.deleteAccessTokenCookie();

        return ResponseEntity.ok()
                .header(SET_COOKIE, cookie.toString())
                .build();
    }

}
