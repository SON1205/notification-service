package com.jjino.notificationservice.global.auth;

import static com.jjino.notificationservice.global.common.Constants.ATTR_AUTH_ERROR;
import static com.jjino.notificationservice.global.error.ErrorCode.EXPIRED_TOKEN;
import static com.jjino.notificationservice.global.error.ErrorCode.INVALID_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider);
        SecurityContextHolder.clearContext();
    }

    private Claims createClaims(Long userId, String role) {
        String secret = Base64.getEncoder()
                .encodeToString("test-secret-key-for-notification-service".getBytes());
        JwtTokenProvider realProvider = new JwtTokenProvider(secret, 3600000L);
        String token = realProvider.generateToken(userId, role);
        return realProvider.parseClaims(token).orElseThrow();
    }

    @Test
    @DisplayName("Bearer 토큰이 유효하면 SecurityContext에 인증 정보를 설정한다")
    void setsAuthenticationForValidBearerToken() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");

        Claims claims = createClaims(1L, "USER");
        given(jwtTokenProvider.parseClaimsOrThrow("valid-token")).willReturn(Optional.of(claims));
        given(jwtTokenProvider.getUserId(claims)).willReturn(1L);
        given(jwtTokenProvider.getRole(claims)).willReturn("USER");

        // when
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Cookie의 access_token으로 인증에 사용된다")
    void setsAuthenticationForCookieToken() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "cookie-token"));

        Claims claims = createClaims(1L, "USER");
        given(jwtTokenProvider.parseClaimsOrThrow("cookie-token")).willReturn(Optional.of(claims));
        given(jwtTokenProvider.getUserId(claims)).willReturn(1L);
        given(jwtTokenProvider.getRole(claims)).willReturn("USER");

        // when
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(1L);
    }

    @Test
    @DisplayName("토큰이 없으면 SecurityContext가 비어있다")
    void noAuthenticationWithoutToken() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(ATTR_AUTH_ERROR)).isNull();
    }

    @Test
    @DisplayName("만료된 토큰이면 EXPIRED_TOKEN을 request attribute에 저장한다")
    void setsExpiredTokenAttributeForExpiredToken() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer expired-token");
        given(jwtTokenProvider.parseClaimsOrThrow("expired-token"))
                .willThrow(new ExpiredJwtException(null, null, "expired"));

        // when
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(ATTR_AUTH_ERROR)).isEqualTo(EXPIRED_TOKEN);
    }

    @Test
    @DisplayName("변조된 토큰이면 INVALID_TOKEN을 request attribute에 저장한다")
    void setsInvalidTokenAttributeForTamperedToken() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer tampered-token");
        given(jwtTokenProvider.parseClaimsOrThrow("tampered-token"))
                .willThrow(new JwtException("invalid signature"));

        // when
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(ATTR_AUTH_ERROR)).isEqualTo(INVALID_TOKEN);
    }

    @Test
    @DisplayName("header와 cookie가 동시에 있으면 header가 우선한다")
    void headerTakesPrecedenceOverCookie() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer header-token");
        request.setCookies(new Cookie("access_token", "cookie-token"));

        Claims claims = createClaims(1L, "USER");
        given(jwtTokenProvider.parseClaimsOrThrow("header-token")).willReturn(Optional.of(claims));
        given(jwtTokenProvider.getUserId(claims)).willReturn(1L);
        given(jwtTokenProvider.getRole(claims)).willReturn("USER");

        // when
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        then(jwtTokenProvider).should().parseClaimsOrThrow("header-token");
    }

    @Test
    @DisplayName("Bearer 접두사 없는 Authorization 헤더는 무시한다")
    void ignoresNonBearerHeader() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic some-credentials");

        // when
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
