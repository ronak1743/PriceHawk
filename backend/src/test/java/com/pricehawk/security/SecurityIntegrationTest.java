package com.pricehawk.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricehawk.dto.RegisterRequest;
import com.pricehawk.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private final String testSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Public endpoint: GET /health is accessible without token")
    void publicEndpoint_health_staysOpenWithoutToken() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Public endpoint: POST /auth/register is accessible without token")
    void publicEndpoint_register_staysOpenWithoutToken() throws Exception {
        RegisterRequest request = new RegisterRequest("publicuser", "public@example.com", "password123");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Protected endpoint: without token returns 401 Unauthorized")
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/test/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint: with malformed token returns 401 Unauthorized")
    void protectedEndpoint_withMalformedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/test/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.malformed.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint: with non-Bearer auth scheme returns 401 Unauthorized")
    void protectedEndpoint_withNonBearerScheme_returns401() throws Exception {
        mockMvc.perform(get("/api/test/protected")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint: with expired token returns 401 Unauthorized")
    void protectedEndpoint_withExpiredToken_returns401() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(HexFormat.of().parseHex(testSecret));
        Date past = new Date(System.currentTimeMillis() - 10000);

        String expiredToken = Jwts.builder()
                .subject("expired@example.com")
                .claim("userId", 10L)
                .issuedAt(new Date(past.getTime() - 60000))
                .expiration(past)
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/test/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint: with tampered token signature returns 401 Unauthorized")
    void protectedEndpoint_withTamperedToken_returns401() throws Exception {
        String validToken = jwtService.generateToken(1L, "user@example.com");
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "ZZZZZ";

        mockMvc.perform(get("/api/test/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint: with valid JWT token returns 200 OK and user identity")
    void protectedEndpoint_withValidToken_returns200() throws Exception {
        RegisterRequest register = new RegisterRequest("tokenuser", "tokenuser@example.com", "password123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        String token = jwtService.generateToken(1L, "tokenuser@example.com");

        mockMvc.perform(get("/api/test/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("protected content"))
                .andExpect(jsonPath("$.user").value("tokenuser@example.com"));
    }

    @Test
    @DisplayName("CORS allows requests from Chrome Extension")
    void cors_preflightRequestFromExtension_allowed() throws Exception {
        mockMvc.perform(options("/api/test/protected")
                .header(HttpHeaders.ORIGIN, "chrome-extension://abcdefghijklmnop")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "chrome-extension://abcdefghijklmnop"));
    }
}
