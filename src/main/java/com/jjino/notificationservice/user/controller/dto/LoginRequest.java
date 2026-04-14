package com.jjino.notificationservice.user.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "username is required")
        @Size(max = 20, message = "Invalid credentials")
        String username,

        @NotBlank(message = "password is required")
        @Size(max = 100, message = "Invalid credentials")
        String password
) {
}
