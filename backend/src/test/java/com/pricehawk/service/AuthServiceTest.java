package com.pricehawk.service;

import com.pricehawk.dto.RegisterRequest;
import com.pricehawk.dto.UserResponse;
import com.pricehawk.exception.EmailAlreadyExistsException;
import com.pricehawk.model.User;
import com.pricehawk.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("register - success encodes password and saves user")
    void register_successfulRegistration_encodesPasswordAndSavesUser() {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "plainPassword123");

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plainPassword123")).thenReturn("$2a$10$encodedHash");

        User savedUser = new User("alice", "alice@example.com", "$2a$10$encodedHash");
        // Simulate DB generating ID
        org.springframework.test.util.ReflectionTestUtils.setField(savedUser, "id", 100L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();
        assertThat(capturedUser.getUsername()).isEqualTo("alice");
        assertThat(capturedUser.getEmail()).isEqualTo("alice@example.com");
        assertThat(capturedUser.getPassword()).isEqualTo("$2a$10$encodedHash");
    }

    @Test
    @DisplayName("register - duplicate email throws EmailAlreadyExistsException without saving")
    void register_duplicateEmail_throwsEmailAlreadyExistsException() {
        RegisterRequest request = new RegisterRequest("bob", "bob@example.com", "password123");

        when(userRepository.existsByEmail("bob@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("register - normalizes email and trims username")
    void register_normalizesEmailAndTrimsUsername() {
        RegisterRequest request = new RegisterRequest("  alice_user  ", "  Alice.User@Example.COM  ", "password123");

        when(userRepository.existsByEmail("alice.user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encodedHash");

        User savedUser = new User("alice_user", "alice.user@example.com", "$2a$10$encodedHash");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        authService.register(request);

        verify(userRepository).existsByEmail("alice.user@example.com");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User captured = userCaptor.getValue();
        assertThat(captured.getUsername()).isEqualTo("alice_user");
        assertThat(captured.getEmail()).isEqualTo("alice.user@example.com");
    }
}
