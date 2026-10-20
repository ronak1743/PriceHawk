package com.pricehawk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricehawk.dto.RegisterRequest;
import com.pricehawk.model.User;
import com.pricehawk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.pricehawk.security.JwtService jwtService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /auth/register - success returns 201 Created and user details")
    void register_withValidCredentials_returns201AndUser() throws Exception {
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", "password123");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));

        Optional<User> savedUser = userRepository.findByEmail("test@example.com");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("POST /auth/register - duplicate email returns 409 Conflict")
    void register_withDuplicateEmail_returns409Conflict() throws Exception {
        RegisterRequest user1 = new RegisterRequest("user1", "duplicate@example.com", "password123");
        RegisterRequest user2 = new RegisterRequest("user2", "duplicate@example.com", "password123");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user2)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /auth/register - duplicate email with different case returns 409 Conflict")
    void register_withDuplicateEmailCaseInsensitive_returns409Conflict() throws Exception {
        RegisterRequest user1 = new RegisterRequest("user1", "casesensitive@example.com", "password123");
        RegisterRequest user2 = new RegisterRequest("user2", "CaseSensitive@EXAMPLE.COM", "password123");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user2)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /auth/register - duplicate username with different email is allowed (returns 201 Created)")
    void register_withDuplicateUsernameDifferentEmail_isAllowed() throws Exception {
        RegisterRequest user1 = new RegisterRequest("sameusername", "user1@example.com", "password123");
        RegisterRequest user2 = new RegisterRequest("sameusername", "user2@example.com", "password123");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user2)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /auth/register - invalid username returns 400 Bad Request")
    void register_withInvalidUsername_returns400BadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("ab", "valid@example.com", "password123");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/register - invalid email format returns 400 Bad Request")
    void register_withInvalidEmail_returns400BadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("testuser", "not-a-valid-email", "password123");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/register - password shorter than 6 chars returns 400 Bad Request")
    void register_withShortPassword_returns400BadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("testuser", "shortpass@example.com", "12345");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/register - password is encrypted with BCrypt and not stored in plain text")
    void register_storesPasswordAsBcryptHashNotPlainText() throws Exception {
        String rawPassword = "mySecurePassword!";
        RegisterRequest request = new RegisterRequest("secureuser", "secure@example.com", rawPassword);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail("secure@example.com")
                .orElseThrow(() -> new AssertionError("User was not saved"));

        assertThat(user.getPassword()).isNotEqualTo(rawPassword);
        assertThat(user.getPassword()).startsWith("$2a$");
        assertThat(passwordEncoder.matches(rawPassword, user.getPassword())).isTrue();
    }

    @Test
    @DisplayName("POST /auth/login - valid credentials returns 200 OK and JWT token")
    void login_withValidCredentials_returns200AndJwtToken() throws Exception {
        RegisterRequest register = new RegisterRequest("john", "john@example.com", "securePassword123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        com.pricehawk.dto.LoginRequest login = new com.pricehawk.dto.LoginRequest("john@example.com", "securePassword123");
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.username").value("john"))
                .andReturn();

        com.pricehawk.dto.LoginResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), com.pricehawk.dto.LoginResponse.class);

        assertThat(jwtService.extractEmail(response.getToken())).isEqualTo("john@example.com");
        assertThat(jwtService.extractUserId(response.getToken())).isNotNull();
        assertThat(jwtService.extractExpiration(response.getToken())).isAfter(new java.util.Date());
    }

    @Test
    @DisplayName("POST /auth/login - wrong password returns 401 Unauthorized")
    void login_withWrongPassword_returns401Unauthorized() throws Exception {
        RegisterRequest register = new RegisterRequest("john", "john2@example.com", "securePassword123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        com.pricehawk.dto.LoginRequest login = new com.pricehawk.dto.LoginRequest("john2@example.com", "wrongPassword");
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/login - unknown email returns 401 Unauthorized")
    void login_withUnknownEmail_returns401Unauthorized() throws Exception {
        com.pricehawk.dto.LoginRequest login = new com.pricehawk.dto.LoginRequest("nobody@example.com", "password123");
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/login - invalid email format returns 400 Bad Request")
    void login_withInvalidEmailFormat_returns400BadRequest() throws Exception {
        com.pricehawk.dto.LoginRequest login = new com.pricehawk.dto.LoginRequest("not-an-email", "password123");
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isBadRequest());
    }
}
