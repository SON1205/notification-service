package com.jjino.notificationservice.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
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
        // 실제 JwtTokenProvider를 사용하여 유효한 Claims 생성
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
        given(jwtTokenProvider.parseClaims("valid-token")).willReturn(Optional.of(claims));
        given(jwtTokenProvider.getUserId(claims)).willReturn(1L);
        given(jwtTokenProvider.getRole(claims)).willReturn("USER");

        // when
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(1L);
    }

    @Test
    @DisplayName("query parameter의 token도 인증에 사용된다")
    void setsAuthenticationForTokenParam() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("token", "param-token");

        Claims claims = createClaims(1L, "USER");
        given(jwtTokenProvider.parseClaims("param-token")).willReturn(Optional.of(claims));
        given(jwtTokenProvider.getUserId(claims)).willReturn(1L);
        given(jwtTokenProvider.getRole(claims)).willReturn("USER");

        // when
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
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
    }

    @Test
    @DisplayName("잘못된 토큰이면 SecurityContext가 비어있다")
    void noAuthenticationForInvalidToken() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        given(jwtTokenProvider.parseClaims("invalid-token")).willReturn(Optional.empty());

        // when
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("header와 query param이 동시에 있으면 header가 우선한다")
    void headerTakesPrecedenceOverQueryParam() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer header-token");
        request.setParameter("token", "param-token");

        Claims claims = createClaims(1L, "USER");
        given(jwtTokenProvider.parseClaims("header-token")).willReturn(Optional.of(claims));
        given(jwtTokenProvider.getUserId(claims)).willReturn(1L);
        given(jwtTokenProvider.getRole(claims)).willReturn("USER");

        // when
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        // header-token이 사용됨 (param-token의 parseClaims는 호출되지 않음)
        then(jwtTokenProvider).should().parseClaims("header-token");
        then(jwtTokenProvider).shouldHaveNoMoreInteractions();
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
