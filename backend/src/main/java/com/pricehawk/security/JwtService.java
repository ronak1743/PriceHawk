package com.pricehawk.security;

import java.util.Date;

public interface JwtService {
    String generateToken(Long userId, String email);
    String extractEmail(String token);
    Long extractUserId(String token);
    boolean isTokenValid(String token, String email);
    Date extractExpiration(String token);
}
