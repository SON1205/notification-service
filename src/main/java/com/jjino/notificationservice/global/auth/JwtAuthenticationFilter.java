package com.jjino.notificationservice.global.auth;

import static com.jjino.notificationservice.global.common.Constants.COOKIE_ACCESS_TOKEN;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

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
            jwtTokenProvider.parseClaims(token).ifPresent(claims -> {
                UserPrincipal principal = new UserPrincipal(
                        jwtTokenProvider.getUserId(claims),
                        jwtTokenProvider.getRole(claims)
                );
                SecurityContextHolder.getContext().setAuthentication(principal);
            });
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
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_ACCESS_TOKEN.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
