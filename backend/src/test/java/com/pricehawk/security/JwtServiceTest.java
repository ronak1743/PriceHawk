package com.pricehawk.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

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
    @DisplayName("generateToken should produce a non-null JWT containing email, userId, and future expiry")
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
}
