package com.pricehawk.service;

import com.pricehawk.dto.RegisterRequest;
import com.pricehawk.dto.UserResponse;
import com.pricehawk.exception.EmailAlreadyExistsException;
import com.pricehawk.model.User;
import com.pricehawk.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.pricehawk.security.JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, com.pricehawk.security.JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("Email already registered: " + normalizedEmail);
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = new User(request.getUsername().trim(), normalizedEmail, encodedPassword);
        User savedUser = userRepository.save(user);

        return new UserResponse(savedUser.getId(), savedUser.getUsername(), savedUser.getEmail());
    }

    public com.pricehawk.dto.LoginResponse login(com.pricehawk.dto.LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new com.pricehawk.exception.InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new com.pricehawk.exception.InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new com.pricehawk.dto.LoginResponse(token, user.getId(), user.getUsername(), user.getEmail());
    }
}
