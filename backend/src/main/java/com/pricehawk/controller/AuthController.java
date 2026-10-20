package com.pricehawk.controller;

import com.pricehawk.dto.RegisterRequest;
import com.pricehawk.dto.UserResponse;
import com.pricehawk.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<com.pricehawk.dto.LoginResponse> login(@Valid @RequestBody com.pricehawk.dto.LoginRequest request) {
        com.pricehawk.dto.LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
