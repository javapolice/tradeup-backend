package com.tradeup.backend.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final long EXPIRATION_SECONDS = 3600;

    private JwtTokenProvider provider;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = randomBytes(32);
        secretKey = Keys.hmacShaKeyFor(keyBytes);
        provider = new JwtTokenProvider(Base64.getEncoder().encodeToString(keyBytes), EXPIRATION_SECONDS);
    }

    @Test
    void 발급한_JWT에서_원래_회원_ID를_추출한다() {
        Long memberId = 42L;

        String token = provider.generateAccessToken(memberId);

        assertThat(provider.getMemberId(token)).isEqualTo(memberId);
    }

    @Test
    void 다른_비밀키로_검증하면_서명_오류가_발생한다() {
        String token = provider.generateAccessToken(42L);
        JwtTokenProvider otherProvider = new JwtTokenProvider(
                Base64.getEncoder().encodeToString(randomBytes(32)), EXPIRATION_SECONDS);

        assertThatThrownBy(() -> otherProvider.getMemberId(token))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void Payload의_회원_ID를_변조하면_서명_오류가_발생한다() {
        String token = provider.generateAccessToken(42L);
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tamperedPayload = payload.replace("\"sub\":\"42\"", "\"sub\":\"43\"");
        assertThat(tamperedPayload).isNotEqualTo(payload);

        // 헤더와 원래 서명은 유지하고 유효한 JSON Payload만 변경한다.
        String tamperedToken = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tamperedPayload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertThatThrownBy(() -> provider.getMemberId(tamperedToken))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void 동일한_키로_서명했어도_과거에_만료된_JWT는_거부한다() {
        String expiredToken = Jwts.builder()
                .subject("42")
                .issuedAt(Date.from(Instant.parse("2000-01-01T00:00:00Z")))
                .expiration(Date.from(Instant.parse("2000-01-01T01:00:00Z")))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> provider.getMemberId(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n"})
    void null과_빈_문자열과_공백_토큰은_거부한다(String token) {
        assertThatThrownBy(() -> provider.getMemberId(token))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("토큰이 비어있습니다.");
    }

    @Test
    void null_회원_ID로는_발급할_수_없다() {
        assertThatThrownBy(() -> provider.generateAccessToken(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("회원 ID가 Null입니다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 31})
    void 디코딩된_비밀키가_32바이트_미만이면_생성할_수_없다(int byteLength) {
        String shortSecret = Base64.getEncoder().encodeToString(randomBytes(byteLength));

        assertThatThrownBy(() -> new JwtTokenProvider(shortSecret, EXPIRATION_SECONDS))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("32 byte 미만 오류입니다.");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void 만료_시간이_0_이하면_생성할_수_없다(long expirationSeconds) {
        String secret = Base64.getEncoder().encodeToString(randomBytes(32));

        assertThatThrownBy(() -> new JwtTokenProvider(secret, expirationSeconds))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT 만료 시간은 0보다 커야 합니다.");
    }

    @Test
    void HS256_토큰의_Payload에는_회원_ID와_발급_만료_시각만_포함된다() {
        String token = provider.generateAccessToken(42L);
        Jws<Claims> parsed = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
        Claims claims = parsed.getPayload();

        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(claims).containsOnlyKeys("sub", "iat", "exp");
        assertThat(claims).doesNotContainKeys("password", "passwordHash", "email", "nickname");
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration().toInstant().getEpochSecond()
                - claims.getIssuedAt().toInstant().getEpochSecond()).isEqualTo(EXPIRATION_SECONDS);
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
