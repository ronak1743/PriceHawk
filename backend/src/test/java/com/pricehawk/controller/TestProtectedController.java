package com.pricehawk.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TestProtectedController {

    @GetMapping("/api/test/protected")
    public ResponseEntity<Map<String, String>> getProtectedContent(Authentication authentication) {
        String name = (authentication != null) ? authentication.getName() : "anonymous";
        return ResponseEntity.ok(Map.of("message", "protected content", "user", name));
    }
}
