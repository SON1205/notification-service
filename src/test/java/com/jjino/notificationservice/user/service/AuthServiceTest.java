package com.jjino.notificationservice.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.jjino.notificationservice.global.auth.JwtTokenProvider;
import com.jjino.notificationservice.global.error.BusinessException;
import com.jjino.notificationservice.global.error.ErrorCode;
import com.jjino.notificationservice.user.domain.Role;
import com.jjino.notificationservice.user.domain.User;
import com.jjino.notificationservice.user.repository.UserRepository;
import com.jjino.notificationservice.user.service.dto.LoginCommand;
import com.jjino.notificationservice.user.service.dto.SignupCommand;
import com.jjino.notificationservice.user.service.dto.SignupInfo;
import com.jjino.notificationservice.user.service.dto.TokenInfo;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private User createUser(Long id, String username) {
        User user = User.builder()
                .username(username)
                .password("encoded-password")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Nested
    @DisplayName("signup")
    class Signup {

        @Test
        @DisplayName("정상 회원가입 시 사용자를 저장하고 SignupInfo를 반환한다")
        void successfulSignup() {
            // given
            SignupCommand command = new SignupCommand("testuser", "password123");
            given(userRepository.existsByUsername("testuser")).willReturn(false);
            given(passwordEncoder.encode("password123")).willReturn("encoded");

            // save()가 호출되면 전달된 user에 id를 세팅 (JPA 실제 동작 모사)
            given(userRepository.save(any(User.class))).willAnswer(invocation -> {
                User user = invocation.getArgument(0);
                ReflectionTestUtils.setField(user, "id", 1L);
                return user;
            });

            // when
            SignupInfo result = authService.signup(command);

            // then
            assertThat(result.userId()).isEqualTo(1L);
            assertThat(result.username()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("저장되는 User의 비밀번호가 인코딩된다")
        void passwordIsEncoded() {
            // given
            SignupCommand command = new SignupCommand("testuser", "rawPassword");
            given(userRepository.existsByUsername("testuser")).willReturn(false);
            given(passwordEncoder.encode("rawPassword")).willReturn("encodedPassword");

            given(userRepository.save(any(User.class))).willAnswer(invocation -> {
                User user = invocation.getArgument(0);
                ReflectionTestUtils.setField(user, "id", 1L);
                return user;
            });

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

            // when
            authService.signup(command);

            // then
            then(userRepository).should().save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPassword()).isEqualTo("encodedPassword");
            assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("중복 username이면 저장/인코딩 없이 예외를 던진다")
        void doesNotSaveOrEncodeOnDuplicateUsername() {
            // given
            SignupCommand command = new SignupCommand("existing", "password123");
            given(userRepository.existsByUsername("existing")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.signup(command))
                    .isInstanceOf(BusinessException.class);
            then(passwordEncoder).shouldHaveNoInteractions();
            then(userRepository).should().existsByUsername("existing");
            then(userRepository).shouldHaveNoMoreInteractions();
        }

        @Test
        @DisplayName("중복 username이면 BusinessException을 던진다")
        void throwsOnDuplicateUsername() {
            // given
            SignupCommand command = new SignupCommand("existing", "password123");
            given(userRepository.existsByUsername("existing")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.signup(command))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::getErrorCode)
                    .isEqualTo(ErrorCode.DUPLICATE_USERNAME);
        }

        @Test
        @DisplayName("DB 레벨 unique 제약 위반 시에도 BusinessException을 던진다")
        void throwsOnDataIntegrityViolation() {
            // given
            SignupCommand command = new SignupCommand("testuser", "password123");
            given(userRepository.existsByUsername("testuser")).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            given(userRepository.save(any(User.class)))
                    .willThrow(new DataIntegrityViolationException("duplicate"));

            // when & then
            assertThatThrownBy(() -> authService.signup(command))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::getErrorCode)
                    .isEqualTo(ErrorCode.DUPLICATE_USERNAME);
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("정상 로그인 시 토큰을 반환한다")
        void successfulLogin() {
            // given
            LoginCommand command = new LoginCommand("testuser", "password123");
            User user = createUser(1L, "testuser");
            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("password123", "encoded-password")).willReturn(true);
            given(jwtTokenProvider.generateToken(1L, "USER")).willReturn("jwt-token");

            // when
            TokenInfo result = authService.login(command);

            // then
            assertThat(result.token()).isEqualTo("jwt-token");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 로그인 시 BusinessException을 던진다")
        void throwsOnUserNotFound() {
            // given
            LoginCommand command = new LoginCommand("nouser", "password123");
            given(userRepository.findByUsername("nouser")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.login(command))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::getErrorCode)
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("비밀번호 불일치 시 BusinessException을 던진다")
        void throwsOnWrongPassword() {
            // given
            LoginCommand command = new LoginCommand("testuser", "wrongpass");
            User user = createUser(1L, "testuser");
            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrongpass", "encoded-password")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.login(command))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::getErrorCode)
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("로그인 실패 시 토큰을 발급하지 않는다")
        void doesNotGenerateTokenOnFailure() {
            // given
            LoginCommand command = new LoginCommand("nouser", "password123");
            given(userRepository.findByUsername("nouser")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.login(command))
                    .isInstanceOf(BusinessException.class);
            then(jwtTokenProvider).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("withdraw")
    class Withdraw {

        @Test
        @DisplayName("정상 탈퇴 시 사용자를 삭제한다")
        void successfulWithdraw() {
            // given
            User user = createUser(1L, "testuser");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            // when
            authService.withdraw(1L);

            // then
            then(userRepository).should().delete(user);
        }

        @Test
        @DisplayName("존재하지 않는 사용자 탈퇴 시 BusinessException을 던진다")
        void throwsOnUserNotFound() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.withdraw(999L))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::getErrorCode)
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }
}
