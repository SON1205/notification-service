package com.jjino.notificationservice.user.controller;

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

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupInfo info = authService.signup(new SignupCommand(request.username(), request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new SignupResponse(info.userId(), info.username()));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenInfo tokenInfo = authService.login(new LoginCommand(request.username(), request.password()));
        return ResponseEntity.ok(new LoginResponse(tokenInfo.token()));
    }

}
