package com.jjino.notificationservice.user.service;

import static com.jjino.notificationservice.global.error.ErrorCode.DUPLICATE_USERNAME;
import static com.jjino.notificationservice.global.error.ErrorCode.INVALID_CREDENTIALS;
import static com.jjino.notificationservice.global.error.ErrorCode.USER_NOT_FOUND;

import com.jjino.notificationservice.global.auth.JwtTokenProvider;
import com.jjino.notificationservice.global.error.BusinessException;
import com.jjino.notificationservice.user.domain.Role;
import com.jjino.notificationservice.user.domain.User;
import com.jjino.notificationservice.user.repository.UserRepository;
import com.jjino.notificationservice.user.service.dto.LoginCommand;
import com.jjino.notificationservice.user.service.dto.SignupCommand;
import com.jjino.notificationservice.user.service.dto.SignupInfo;
import com.jjino.notificationservice.user.service.dto.TokenInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public SignupInfo signup(SignupCommand command) {
        if (userRepository.existsByUsername(command.username())) {
            throw new BusinessException(DUPLICATE_USERNAME);
        }

        User user = User.builder()
                .username(command.username())
                .password(passwordEncoder.encode(command.password()))
                .role(Role.USER)
                .build();

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(DUPLICATE_USERNAME);
        }

        return new SignupInfo(user.getId(), user.getUsername());
    }

    public TokenInfo login(LoginCommand command) {
        User user = userRepository.findByUsername(command.username())
                .orElseThrow(() -> new BusinessException(INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(command.password(), user.getPassword())) {
            throw new BusinessException(INVALID_CREDENTIALS);
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getRole().name());
        return new TokenInfo(token);
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
        userRepository.delete(user);
    }
}
