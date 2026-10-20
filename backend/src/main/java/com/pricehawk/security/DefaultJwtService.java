package com.pricehawk.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class DefaultJwtService implements JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    @Override
    public String generateToken(Long userId, String email) {
        return null;
    }

    @Override
    public String extractEmail(String token) {
        return null;
    }

    @Override
    public Long extractUserId(String token) {
        return null;
    }

    @Override
    public boolean isTokenValid(String token, String email) {
        return false;
    }

    @Override
    public Date extractExpiration(String token) {
        return null;
    }
}
