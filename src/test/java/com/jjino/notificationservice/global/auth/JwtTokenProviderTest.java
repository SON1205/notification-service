package com.jjino.notificationservice.global.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("test-secret-key-for-notification-service".getBytes());
    private static final long EXPIRATION = 3600000L;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, EXPIRATION);
    }

    @Nested
    @DisplayName("generateToken")
    class GenerateToken {

        @Test
        @DisplayName("유효한 JWT 토큰을 생성한다")
        void generatesValidToken() {
            // when
            String token = jwtTokenProvider.generateToken(1L, "USER");

            // then
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("생성된 토큰에서 userId와 role을 추출할 수 있다")
        void tokenContainsCorrectClaims() {
            // given
            String token = jwtTokenProvider.generateToken(1L, "USER");

            // when
            Optional<Claims> claims = jwtTokenProvider.parseClaims(token);

            // then
            assertThat(claims).isPresent();
            assertThat(jwtTokenProvider.getUserId(claims.get())).isEqualTo(1L);
            assertThat(jwtTokenProvider.getRole(claims.get())).isEqualTo("USER");
        }
    }

    @Nested
    @DisplayName("parseClaims")
    class ParseClaims {

        @Test
        @DisplayName("유효한 토큰을 파싱하면 Claims를 반환한다")
        void returnsClaimsForValidToken() {
            // given
            String token = jwtTokenProvider.generateToken(1L, "ADMIN");

            // when
            Optional<Claims> claims = jwtTokenProvider.parseClaims(token);

            // then
            assertThat(claims).isPresent();
            assertThat(jwtTokenProvider.getRole(claims.get())).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("잘못된 토큰을 파싱하면 empty를 반환한다")
        void returnsEmptyForInvalidToken() {
            // when
            Optional<Claims> claims = jwtTokenProvider.parseClaims("invalid.token.here");

            // then
            assertThat(claims).isEmpty();
        }

        @Test
        @DisplayName("만료된 토큰을 파싱하면 empty를 반환한다")
        void returnsEmptyForExpiredToken() {
            // given - 만료 시간 0ms인 provider
            JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, 0L);
            String token = expiredProvider.generateToken(1L, "USER");

            // when
            Optional<Claims> claims = jwtTokenProvider.parseClaims(token);

            // then
            assertThat(claims).isEmpty();
        }

        @Test
        @DisplayName("다른 키로 서명된 토큰을 파싱하면 empty를 반환한다")
        void returnsEmptyForTokenWithDifferentKey() {
            // given
            String otherSecret = Base64.getEncoder()
                    .encodeToString("another-secret-key-for-testing-only!".getBytes());
            JwtTokenProvider otherProvider = new JwtTokenProvider(otherSecret, EXPIRATION);
            String token = otherProvider.generateToken(1L, "USER");

            // when
            Optional<Claims> claims = jwtTokenProvider.parseClaims(token);

            // then
            assertThat(claims).isEmpty();
        }
    }
}
