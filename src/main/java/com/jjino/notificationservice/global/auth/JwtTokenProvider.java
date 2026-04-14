package com.jjino.notificationservice.global.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expiration;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiration}") long expiration) {
        this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.expiration = expiration;
    }

    public String generateToken(Long userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 토큰을 파싱하고 검증한다. 실패 시 예외를 그대로 던진다.
     * - ExpiredJwtException: 토큰 만료
     * - JwtException: 변조, 형식 오류 등
     * JwtAuthenticationFilter에서 예외 종류별로 request attribute에 원인을 저장하는 데 사용.
     */
    public Optional<Claims> parseClaimsOrThrow(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Optional.of(claims);
    }

    /**
     * 토큰을 파싱하고 검증한다. 실패 시 empty 반환.
     */
    public Optional<Claims> parseClaims(String token) {
        try {
            return parseClaimsOrThrow(token);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Long getUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public long getExpirationSeconds() {
        return expiration / 1000;
    }
}
