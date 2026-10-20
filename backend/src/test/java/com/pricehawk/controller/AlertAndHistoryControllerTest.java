package com.pricehawk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricehawk.dto.TrackProductRequest;
import com.pricehawk.model.ItemSource;
import com.pricehawk.model.Platform;
import com.pricehawk.model.PriceAlert;
import com.pricehawk.model.PriceHistory;
import com.pricehawk.model.TrackedItem;
import com.pricehawk.model.User;
import com.pricehawk.repository.*;
import com.pricehawk.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AlertAndHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrackedItemRepository trackedItemRepository;

    @Autowired
    private ItemSourceRepository itemSourceRepository;

    @Autowired
    private PriceHistoryRepository priceHistoryRepository;

    @Autowired
    private PriceAlertRepository priceAlertRepository;

    @Autowired
    private JwtService jwtService;

    private User user1;
    private User user2;
    private String token1;
    private String token2;
    private TrackedItem item1;
    private ItemSource source1;

    @BeforeEach
    void setUp() {
        priceAlertRepository.deleteAll();
        priceHistoryRepository.deleteAll();
        itemSourceRepository.deleteAll();
        trackedItemRepository.deleteAll();
        userRepository.deleteAll();

        user1 = userRepository.save(new User("user1", "user1@example.com", "$2a$10$hash"));
        user2 = userRepository.save(new User("user2", "user2@example.com", "$2a$10$hash"));

        token1 = jwtService.generateToken(user1.getId(), user1.getEmail());
        token2 = jwtService.generateToken(user2.getId(), user2.getEmail());

        item1 = trackedItemRepository.save(new TrackedItem(user1, "iPhone 15", new BigDecimal("65000.00")));
        source1 = itemSourceRepository.save(new ItemSource(
                item1, Platform.AMAZON, "https://amazon.in/dp/xyz",
                new BigDecimal("69000.00"), new BigDecimal("69000.00"), "INR", "http://img.jpg"
        ));
    }

    @Test
    @DisplayName("GET /products/{id}/history without token returns 401 Unauthorized")
    void getHistory_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/products/" + item1.getId() + "/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /products/{id}/history with valid token returns price history points")
    void getHistory_withToken_returnsHistory() throws Exception {
        priceHistoryRepository.save(new PriceHistory(source1, new BigDecimal("69000.00"), true));
        priceHistoryRepository.save(new PriceHistory(source1, new BigDecimal("64500.00"), true));

        mockMvc.perform(get("/products/" + item1.getId() + "/history")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].price").value(69000.00))
                .andExpect(jsonPath("$[1].price").value(64500.00));
    }

    @Test
    @DisplayName("GET /products/{id}/history when product belongs to another user returns 404 Not Found")
    void getHistory_notOwned_returns404() throws Exception {
        mockMvc.perform(get("/products/" + item1.getId() + "/history")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token2))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /alerts returns user's alerts")
    void getAlerts_returnsAlerts() throws Exception {
        priceAlertRepository.save(new PriceAlert(item1, user1, new BigDecimal("69000.00"),
                new BigDecimal("64000.00"), new BigDecimal("65000.00")));

        mockMvc.perform(get("/alerts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].oldPrice").value(69000.00))
                .andExpect(jsonPath("$[0].newPrice").value(64000.00))
                .andExpect(jsonPath("$[0].isRead").value(false));
    }

    @Test
    @DisplayName("PATCH /alerts/{id}/read marks alert as read")
    void markAlertAsRead_updatesStatus() throws Exception {
        PriceAlert alert = priceAlertRepository.save(new PriceAlert(item1, user1, new BigDecimal("69000.00"),
                new BigDecimal("64000.00"), new BigDecimal("65000.00")));

        mockMvc.perform(patch("/alerts/" + alert.getId() + "/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isOk());

        PriceAlert updated = priceAlertRepository.findById(alert.getId()).orElseThrow();
        assertThat(updated.isRead()).isTrue();
    }
}
