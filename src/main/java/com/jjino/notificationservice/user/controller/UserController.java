package com.jjino.notificationservice.user.controller;

import com.jjino.notificationservice.global.auth.CurrentUserId;
import com.jjino.notificationservice.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@CurrentUserId Long userId) {
        authService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }
}
