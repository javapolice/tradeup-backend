package com.tradeup.backend.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.time.Instant;
import java.util.Base64;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtTokenProvider(
            @Value("${jwt.secret-base64}") String secretBase64,
            @Value("${jwt.access-token-expiration-seconds}") long expirationSeconds
    ) {
        byte[] decode = Base64.getDecoder().decode(secretBase64);

        if (decode.length < 32) {
            throw new IllegalArgumentException("32 byte 미만 오류입니다.");
        }

        this.secretKey = Keys.hmacShaKeyFor(decode);

        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException("JWT 만료 시간은 0보다 커야 합니다.");
        }

        this.expirationSeconds = expirationSeconds;
    }

    public String generateAccessToken(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("회원 ID가 Null입니다.");
        }

        Instant now = Instant.now();

        Instant exp = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .subject(memberId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public Long getMemberId(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("토큰이 비어있습니다.");
        }

        Jws<Claims> jws = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);

        String subject = jws.getPayload().getSubject();

        return Long.valueOf(subject);
    }

}
