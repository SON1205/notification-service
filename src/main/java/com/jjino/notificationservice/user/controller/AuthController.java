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

}
