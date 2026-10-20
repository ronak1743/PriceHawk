package com.pricehawk.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private DefaultJwtService jwtService;
    // 256-bit test secret (64 hex characters)
    private final String testSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long testExpirationMs = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtService = new DefaultJwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", testSecret);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", testExpirationMs);
    }

    @Test
    @DisplayName("generateToken produces a valid JWT containing email, userId, and future expiry")
    void generateToken_containsClaimsAndExpiry() {
        String token = jwtService.generateToken(42L, "user@example.com");

        assertThat(token).isNotNull().isNotBlank();
        assertThat(jwtService.extractEmail(token)).isEqualTo("user@example.com");
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);

        Date expiration = jwtService.extractExpiration(token);
        assertThat(expiration).isAfter(new Date());
        assertThat(jwtService.isTokenValid(token, "user@example.com")).isTrue();
        assertThat(jwtService.isTokenValid(token, "wrong@example.com")).isFalse();
    }

    @Test
    @DisplayName("isTokenValid returns false for expired token")
    void isTokenValid_withExpiredToken_returnsFalse() {
        SecretKey key = Keys.hmacShaKeyFor(HexFormat.of().parseHex(testSecret));
        Date past = new Date(System.currentTimeMillis() - 10000); // 10s in the past

        String expiredToken = Jwts.builder()
                .subject("user@example.com")
                .claim("userId", 42L)
                .issuedAt(new Date(past.getTime() - 60000))
                .expiration(past)
                .signWith(key)
                .compact();

        assertThat(jwtService.isTokenValid(expiredToken, "user@example.com")).isFalse();
    }

    @Test
    @DisplayName("isTokenValid returns false for tampered signature or token payload")
    void isTokenValid_withTamperedSignature_returnsFalse() {
        String token = jwtService.generateToken(42L, "user@example.com");
        String tamperedToken = token.substring(0, token.length() - 4) + "XXXX";

        assertThat(jwtService.isTokenValid(tamperedToken, "user@example.com")).isFalse();
    }

    @Test
    @DisplayName("isTokenValid returns false when token signed by a different secret key")
    void isTokenValid_withDifferentSigningKey_returnsFalse() {
        String differentSecret = "994E635266556A586E3272357538782F413F4428472B4B6250645367566B5999";
        SecretKey otherKey = Keys.hmacShaKeyFor(HexFormat.of().parseHex(differentSecret));

        String rogueToken = Jwts.builder()
                .subject("user@example.com")
                .claim("userId", 42L)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(otherKey)
                .compact();

        assertThat(jwtService.isTokenValid(rogueToken, "user@example.com")).isFalse();
    }

    @Test
    @DisplayName("isTokenValid returns false for null, empty, or whitespace tokens")
    void isTokenValid_withNullOrEmptyToken_returnsFalse() {
        assertThat(jwtService.isTokenValid(null, "user@example.com")).isFalse();
        assertThat(jwtService.isTokenValid("", "user@example.com")).isFalse();
        assertThat(jwtService.isTokenValid("   ", "user@example.com")).isFalse();
        assertThat(jwtService.isTokenValid("random.garbage.token", "user@example.com")).isFalse();
    }
}
