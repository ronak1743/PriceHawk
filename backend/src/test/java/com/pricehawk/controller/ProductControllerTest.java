package com.pricehawk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricehawk.dto.RegisterRequest;
import com.pricehawk.dto.TrackProductRequest;
import com.pricehawk.model.User;
import com.pricehawk.model.TrackedItem;
import com.pricehawk.model.ItemSource;
import com.pricehawk.model.PriceHistory;
import com.pricehawk.model.PriceAlert;
import com.pricehawk.repository.ItemSourceRepository;
import com.pricehawk.repository.TrackedItemRepository;
import com.pricehawk.repository.UserRepository;
import com.pricehawk.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.springframework.boot.test.mock.mockito.MockBean;
import com.pricehawk.scraper.PriceScraperService;
import com.pricehawk.scraper.ScrapedProduct;
import com.pricehawk.model.Platform;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

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
    private com.pricehawk.repository.PriceHistoryRepository priceHistoryRepository;

    @Autowired
    private com.pricehawk.repository.PriceAlertRepository priceAlertRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private PriceScraperService priceScraperService;

    @MockBean
    private com.pricehawk.service.CrossPlatformSearchService crossPlatformSearchService;

    private User user1;
    private User user2;
    private String token1;
    private String token2;

    @BeforeEach
    void setUp() {
        priceAlertRepository.deleteAll();
        priceHistoryRepository.deleteAll();
        itemSourceRepository.deleteAll();
        trackedItemRepository.deleteAll();
        userRepository.deleteAll();

        user1 = userRepository.save(new User("userone", "user1@example.com", "$2a$10$dummyHash"));
        user2 = userRepository.save(new User("usertwo", "user2@example.com", "$2a$10$dummyHash"));

        token1 = jwtService.generateToken(user1.getId(), user1.getEmail());
        token2 = jwtService.generateToken(user2.getId(), user2.getEmail());
    }

    @Test
    @DisplayName("POST /products/track without token returns 401 Unauthorized")
    void trackProduct_withoutToken_returns401() throws Exception {
        TrackProductRequest request = new TrackProductRequest(
                "https://www.amazon.in/dp/B08L5WHJ2V",
                "iPhone 12",
                new BigDecimal("49999.00"),
                null,
                "INR",
                null
        );

        mockMvc.perform(post("/products/track")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /products/track with valid token returns 201 Created and persists item")
    void trackProduct_withValidToken_returns201AndPersists() throws Exception {
        TrackProductRequest request = new TrackProductRequest(
                "https://www.amazon.in/dp/B08L5WHJ2V",
                "iPhone 12",
                new BigDecimal("49999.00"),
                new BigDecimal("45000.00"),
                "INR",
                "https://images.example.com/iphone.jpg"
        );

        mockMvc.perform(post("/products/track")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("iPhone 12"))
                .andExpect(jsonPath("$.targetPrice").value(45000.00))
                .andExpect(jsonPath("$.sources[0].platform").value("AMAZON"))
                .andExpect(jsonPath("$.sources[0].currentPrice").value(49999.00))
                .andExpect(jsonPath("$.sources[0].url").value("https://www.amazon.in/dp/B08L5WHJ2V"));

        assertThat(trackedItemRepository.count()).isEqualTo(1);
        assertThat(itemSourceRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /products/track with invalid input returns 400 Bad Request")
    void trackProduct_withBlankTitleOrNullPrice_returns400() throws Exception {
        TrackProductRequest badRequest = new TrackProductRequest(
                "",
                "",
                null,
                null,
                null,
                null
        );

        mockMvc.perform(post("/products/track")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /products without token returns 401 Unauthorized")
    void getProducts_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /products with valid token returns only items belonging to the authenticated user")
    void getProducts_returnsOnlyUserItems() throws Exception {
        // Track item for user 1
        TrackProductRequest req1 = new TrackProductRequest(
                "https://www.amazon.in/dp/B08L5WHJ2V",
                "User 1 Product",
                new BigDecimal("1000.00"),
                null,
                "INR",
                null
        );
        mockMvc.perform(post("/products/track")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Track item for user 2
        TrackProductRequest req2 = new TrackProductRequest(
                "https://www.flipkart.com/item2",
                "User 2 Product",
                new BigDecimal("2000.00"),
                null,
                "INR",
                null
        );
        mockMvc.perform(post("/products/track")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        // Fetch user 1 items
        mockMvc.perform(get("/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("User 1 Product"));

        // Fetch user 2 items
        mockMvc.perform(get("/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("User 2 Product"));
    }

    @Test
    @DisplayName("DELETE /products/{id} without token returns 401 Unauthorized")
    void deleteProduct_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/products/123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /products/{id} with valid token deletes item, its history, and alerts, returning 204 No Content")
    void deleteProduct_withValidToken_returns204() throws Exception {
        TrackProductRequest req = new TrackProductRequest(
                "https://www.amazon.in/dp/B08L5WHJ2V",
                "Product to delete",
                new BigDecimal("1000.00"),
                new BigDecimal("900.00"),
                "INR",
                null
        );
        MvcResult res = mockMvc.perform(post("/products/track")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        long productId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
        TrackedItem item = trackedItemRepository.findById(productId).orElseThrow();
        ItemSource source = itemSourceRepository.findAll().stream()
                .filter(s -> s.getTrackedItem().getId().equals(productId))
                .findFirst().orElseThrow();

        // Simulate recorded price history & triggered price alerts for this product
        priceHistoryRepository.save(new com.pricehawk.model.PriceHistory(source, new BigDecimal("950.00"), true));
        priceAlertRepository.save(new com.pricehawk.model.PriceAlert(item, user1, new BigDecimal("1000.00"), new BigDecimal("900.00"), item.getTargetPrice()));

        mockMvc.perform(delete("/products/" + productId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isNoContent());

        assertThat(trackedItemRepository.findById(productId)).isEmpty();
        assertThat(priceHistoryRepository.findByItemSourceTrackedItemIdOrderByRecordedAtAsc(productId)).isEmpty();
        assertThat(priceAlertRepository.findByUserIdOrderByCreatedAtDesc(user1.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /products/{id} when item belongs to another user returns 404 Not Found")
    void deleteProduct_ownedByAnotherUser_returns404() throws Exception {
        TrackProductRequest req = new TrackProductRequest(
                "https://www.amazon.in/dp/B08L5WHJ2V",
                "User 1 product",
                new BigDecimal("1000.00"),
                null,
                "INR",
                null
        );
        MvcResult res = mockMvc.perform(post("/products/track")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        long productId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // User 2 tries to delete user 1's product
        mockMvc.perform(delete("/products/" + productId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token2))
                .andExpect(status().isNotFound());

        // Ensure item was not deleted
        assertThat(trackedItemRepository.findById(productId)).isPresent();
    }

    @Test
    @DisplayName("POST /products/{id}/refresh with valid token refreshes price and returns 200 OK")
    void refreshProduct_withValidToken_returns200() throws Exception {
        String url = "https://www.amazon.in/dp/B08L5WHJ2V";
        TrackProductRequest req = new TrackProductRequest(
                url,
                "iPhone 12",
                new BigDecimal("60000.00"),
                null,
                "INR",
                null
        );
        MvcResult res = mockMvc.perform(post("/products/track")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        long productId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        when(priceScraperService.scrapeProduct(url))
                .thenReturn(new ScrapedProduct("iPhone 12", new BigDecimal("54999.00"), "INR", "https://img.jpg", true, Platform.AMAZON));

        mockMvc.perform(post("/products/" + productId + "/refresh")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.sources[0].currentPrice").value(54999.00));
    }

    @Test
    @DisplayName("POST /products/{id}/refresh without token returns 401 Unauthorized")
    void refreshProduct_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/products/1/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /products/{id}/refresh for non-existent product returns 404 Not Found")
    void refreshProduct_nonExistent_returns404() throws Exception {
        mockMvc.perform(post("/products/99999/refresh")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /products/{id}/target-price with valid token updates target price and returns 200 OK")
    void updateTargetPrice_withValidToken_returns200() throws Exception {
        TrackProductRequest req = new TrackProductRequest(
                "https://www.amazon.in/dp/B08L5WHJ2V",
                "Laptop",
                new BigDecimal("50000.00"),
                new BigDecimal("48000.00"),
                "INR",
                null
        );
        MvcResult res = mockMvc.perform(post("/products/track")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        long productId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        com.pricehawk.dto.UpdateTargetPriceRequest updateReq =
                new com.pricehawk.dto.UpdateTargetPriceRequest(new BigDecimal("45000.00"));

        mockMvc.perform(patch("/products/" + productId + "/target-price")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.targetPrice").value(45000.00));

        assertThat(trackedItemRepository.findById(productId).get().getTargetPrice())
                .isEqualByComparingTo(new BigDecimal("45000.00"));
    }

    @Test
    @DisplayName("PATCH /products/{id}/target-price without token returns 401 Unauthorized")
    void updateTargetPrice_withoutToken_returns401() throws Exception {
        com.pricehawk.dto.UpdateTargetPriceRequest updateReq =
                new com.pricehawk.dto.UpdateTargetPriceRequest(new BigDecimal("45000.00"));

        mockMvc.perform(patch("/products/1/target-price")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /products/{id}/alert-settings updates both targetPrice and notifyOnAnyDrop toggle")
    void updateAlertSettings_withValidToken_updatesSettings() throws Exception {
        TrackProductRequest req = new TrackProductRequest(
                "https://www.amazon.in/dp/B08L5WHJ2V",
                "Product alert test",
                new BigDecimal("50000.00"),
                null,
                "INR",
                null
        );
        MvcResult res = mockMvc.perform(post("/products/track")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        long productId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        com.pricehawk.dto.UpdateAlertSettingsRequest updateReq =
                new com.pricehawk.dto.UpdateAlertSettingsRequest(new BigDecimal("42000.00"), false);

        mockMvc.perform(patch("/products/" + productId + "/alert-settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.targetPrice").value(42000.00))
                .andExpect(jsonPath("$.notifyOnAnyDrop").value(false));

        TrackedItem updated = trackedItemRepository.findById(productId).orElseThrow();
        assertThat(updated.getTargetPrice()).isEqualByComparingTo(new BigDecimal("42000.00"));
        assertThat(updated.getNotifyOnAnyDrop()).isFalse();
    }

    @Test
    @DisplayName("PATCH /products/{id}/alert-settings with null targetPrice clears limit and restores normal alert mode")
    void updateAlertSettings_withNullTargetPrice_removesLimitAndRestoresNormalMode() throws Exception {
        TrackProductRequest req = new TrackProductRequest(
                "https://www.amazon.in/dp/B08L5WHJ2V",
                "Product alert remove test",
                new BigDecimal("50000.00"),
                new BigDecimal("45000.00"),
                "INR",
                null
        );
        MvcResult res = mockMvc.perform(post("/products/track")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        long productId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // Clear target price
        com.pricehawk.dto.UpdateAlertSettingsRequest clearReq =
                new com.pricehawk.dto.UpdateAlertSettingsRequest(null, null);

        mockMvc.perform(patch("/products/" + productId + "/alert-settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(clearReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.targetPrice").doesNotExist())
                .andExpect(jsonPath("$.notifyOnAnyDrop").value(true));

        TrackedItem updated = trackedItemRepository.findById(productId).orElseThrow();
        assertThat(updated.getTargetPrice()).isNull();
        assertThat(updated.getNotifyOnAnyDrop()).isTrue();
    }

    @Test
    @DisplayName("PATCH /products/{id}/target-price with negative price returns 400 Bad Request")
    void updateTargetPrice_negativePrice_returns400() throws Exception {
        com.pricehawk.dto.UpdateTargetPriceRequest updateReq =
                new com.pricehawk.dto.UpdateTargetPriceRequest(new BigDecimal("-100.00"));

        mockMvc.perform(patch("/products/1/target-price")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /products/{id}/target-price for another user's item returns 404 Not Found")
    void updateTargetPrice_ownedByAnotherUser_returns404() throws Exception {
        TrackProductRequest req = new TrackProductRequest(
                "https://www.amazon.in/dp/B08L5WHJ2V",
                "Laptop",
                new BigDecimal("50000.00"),
                null,
                "INR",
                null
        );
        MvcResult res = mockMvc.perform(post("/products/track")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        long productId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        com.pricehawk.dto.UpdateTargetPriceRequest updateReq =
                new com.pricehawk.dto.UpdateTargetPriceRequest(new BigDecimal("40000.00"));

        mockMvc.perform(patch("/products/" + productId + "/target-price")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /products/{id}/sources with manual Flipkart URL adds second source and returns 201 Created")
    void addSource_manualUrl_returns201WithMultipleSources() throws Exception {
        TrackProductRequest trackReq = new TrackProductRequest(
                "https://www.amazon.in/dp/B08L5WHJ2V",
                "Apple iPhone 12",
                new BigDecimal("50000.00"),
                null,
                "INR",
                "https://img.amazon.com/iphone.jpg"
        );
        MvcResult res = mockMvc.perform(post("/products/track")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(trackReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long productId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        com.pricehawk.dto.AddProductSourceRequest addReq = new com.pricehawk.dto.AddProductSourceRequest(
                "https://www.flipkart.com/apple-iphone-12/p/itm123?pid=IPHONE12",
                new BigDecimal("48999.00"),
                "https://img.flipkart.com/iphone.jpg",
                false
        );

        mockMvc.perform(post("/products/" + productId + "/sources")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.sources.length()").value(2))
                .andExpect(jsonPath("$.sources[0].platform").value("AMAZON"))
                .andExpect(jsonPath("$.sources[1].platform").value("FLIPKART"))
                .andExpect(jsonPath("$.sources[1].currentPrice").value(48999.00));
    }

    @Test
    @DisplayName("POST /products/{id}/sources with autoDiscover=true finds counterpart on other platform and returns 201 Created")
    void addSource_autoDiscover_returns201WithAutoDiscoveredCounterpart() throws Exception {
        TrackProductRequest trackReq = new TrackProductRequest(
                "https://www.amazon.in/dp/B08L5WHJ2V",
                "Apple iPhone 12",
                new BigDecimal("50000.00"),
                null,
                "INR",
                null
        );
        MvcResult res = mockMvc.perform(post("/products/track")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(trackReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long productId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        when(crossPlatformSearchService.findCounterpartProduct(eq(Platform.AMAZON), anyString(), any(), any()))
                .thenReturn(new ScrapedProduct(
                        "Apple iPhone 12 (Black, 64 GB)",
                        new BigDecimal("47990.00"),
                        "INR",
                        "https://img.flipkart.com/iphone.jpg",
                        "https://www.flipkart.com/apple-iphone-12/p/itm123?pid=IPH12",
                        true,
                        Platform.FLIPKART
                ));

        com.pricehawk.dto.AddProductSourceRequest addReq = new com.pricehawk.dto.AddProductSourceRequest();
        addReq.setAutoDiscover(true);

        mockMvc.perform(post("/products/" + productId + "/sources")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sources.length()").value(2))
                .andExpect(jsonPath("$.sources[1].platform").value("FLIPKART"))
                .andExpect(jsonPath("$.sources[1].currentPrice").value(47990.00));
    }
}
