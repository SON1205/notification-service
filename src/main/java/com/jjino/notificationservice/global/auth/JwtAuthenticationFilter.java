package com.jjino.notificationservice.global.auth;

import static com.jjino.notificationservice.global.common.Constants.ATTR_AUTH_ERROR;
import static com.jjino.notificationservice.global.common.Constants.COOKIE_ACCESS_TOKEN;
import static com.jjino.notificationservice.global.error.ErrorCode.EXPIRED_TOKEN;
import static com.jjino.notificationservice.global.error.ErrorCode.INVALID_TOKEN;

import com.jjino.notificationservice.global.error.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 인증 필터.
 *
 * 서블릿 필터는 Spring MVC 밖에서 동작하므로 여기서 던진 예외는 GlobalExceptionHandler에 잡히지 않는다.
 * 따라서 토큰 파싱 실패 시 예외를 던지지 않고, 실패 원인을 request attribute에 저장한 뒤 통과시킨다.
 *
 * 이후 AuthorizationFilter가 인증 없음을 감지하면 ExceptionTranslationFilter가
 * CustomAuthenticationEntryPoint.commence()를 호출하고,
 * 거기서 request attribute에 저장된 원인을 꺼내 원인별 응답을 반환한다.
 *
 * [흐름]
 * 1. 토큰 파싱 성공 → SecurityContext에 인증 설정 → 정상 진행
 * 2. 토큰 만료 → request.setAttribute(authError, EXPIRED_TOKEN) → 인증 없이 통과
 * 3. 토큰 변조/형식오류 → request.setAttribute(authError, INVALID_TOKEN) → 인증 없이 통과
 * 4. 토큰 없음 → 아무것도 안 함 → 인증 없이 통과
 * → 2,3,4 모두 AuthorizationFilter에서 걸리면 CustomAuthenticationEntryPoint로 이동
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null) {
            try {
                jwtTokenProvider.parseClaimsOrThrow(token).ifPresent(claims -> {
                    UserPrincipal principal = new UserPrincipal(
                            jwtTokenProvider.getUserId(claims),
                            jwtTokenProvider.getRole(claims)
                    );
                    SecurityContextHolder.getContext().setAuthentication(principal);
                });
            } catch (ExpiredJwtException e) {
                request.setAttribute(ATTR_AUTH_ERROR, EXPIRED_TOKEN);
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute(ATTR_AUTH_ERROR, INVALID_TOKEN);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(AUTHORIZATION_HEADER);
        if (bearer != null && bearer.startsWith(BEARER_PREFIX)) {
            return bearer.substring(BEARER_PREFIX.length());
        }

        return resolveTokenFromCookie(request);
    }

    private String resolveTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_ACCESS_TOKEN.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
