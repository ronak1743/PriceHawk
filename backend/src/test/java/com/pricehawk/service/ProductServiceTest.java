package com.pricehawk.service;

import com.pricehawk.dto.ItemSourceResponse;
import com.pricehawk.dto.TrackProductRequest;
import com.pricehawk.dto.TrackedItemResponse;
import com.pricehawk.exception.ResourceNotFoundException;
import com.pricehawk.model.ItemSource;
import com.pricehawk.model.Platform;
import com.pricehawk.model.TrackedItem;
import com.pricehawk.model.User;
import com.pricehawk.repository.ItemSourceRepository;
import com.pricehawk.repository.TrackedItemRepository;
import com.pricehawk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TrackedItemRepository trackedItemRepository;

    @Mock
    private ItemSourceRepository itemSourceRepository;

    @Mock
    private com.pricehawk.repository.PriceHistoryRepository priceHistoryRepository;

    @Mock
    private com.pricehawk.repository.PriceAlertRepository priceAlertRepository;

    @Mock
    private com.pricehawk.scraper.PriceScraperService priceScraperService;

    @Mock
    private PriceTrackingService priceTrackingService;

    @Mock
    private CrossPlatformSearchService crossPlatformSearchService;

    private ProductService productService;

    private User testUser;

    @BeforeEach
    void setUp() {
        productService = new DefaultProductService(
                userRepository,
                trackedItemRepository,
                itemSourceRepository,
                priceHistoryRepository,
                priceAlertRepository,
                priceScraperService,
                priceTrackingService,
                crossPlatformSearchService
        );
        testUser = new User("testuser", "test@example.com", "hash123");
        testUser.setId(1L);
    }

    @Test
    @DisplayName("trackProduct - successfully creates tracked item and auto-detects AMAZON platform")
    void trackProduct_detectsAmazonPlatform_andSavesItem() {
        TrackProductRequest request = new TrackProductRequest(
                "https://www.amazon.in/dp/B08L5WHJ2V",
                "Apple iPhone 12",
                new BigDecimal("49999.00"),
                new BigDecimal("45000.00"),
                "INR",
                "https://m.media-amazon.com/images/I/711wsjBtWeL._SL1500_.jpg"
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        TrackedItem savedItem = new TrackedItem(testUser, request.getTitle(), request.getTargetPrice());
        savedItem.setId(100L);
        savedItem.setCreatedAt(LocalDateTime.now());
        savedItem.setUpdatedAt(LocalDateTime.now());

        ItemSource savedSource = new ItemSource(
                savedItem,
                Platform.AMAZON,
                request.getUrl(),
                request.getCurrentPrice(),
                request.getCurrentPrice(),
                "INR",
                request.getImageUrl()
        );
        savedSource.setId(200L);
        savedItem.setSources(new ArrayList<>(List.of(savedSource)));

        when(trackedItemRepository.save(any(TrackedItem.class))).thenReturn(savedItem);

        TrackedItemResponse response = productService.trackProduct(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getTitle()).isEqualTo("Apple iPhone 12");
        assertThat(response.getTargetPrice()).isEqualByComparingTo("45000.00");
        assertThat(response.getSources()).hasSize(1);

        ItemSourceResponse source = response.getSources().get(0);
        assertThat(source.getId()).isEqualTo(200L);
        assertThat(source.getPlatform()).isEqualTo(Platform.AMAZON);
        assertThat(source.getUrl()).isEqualTo("https://www.amazon.in/dp/B08L5WHJ2V");
        assertThat(source.getCurrentPrice()).isEqualByComparingTo("49999.00");
        assertThat(source.getOriginalPrice()).isEqualByComparingTo("49999.00");
        assertThat(source.getCurrency()).isEqualTo("INR");

        ArgumentCaptor<TrackedItem> itemCaptor = ArgumentCaptor.forClass(TrackedItem.class);
        verify(trackedItemRepository).save(itemCaptor.capture());
        TrackedItem itemToSave = itemCaptor.getValue();
        assertThat(itemToSave.getUser()).isEqualTo(testUser);
        assertThat(itemToSave.getSources().get(0).getPlatform()).isEqualTo(Platform.AMAZON);
    }

    @Test
    @DisplayName("trackProduct - successfully auto-detects FLIPKART platform")
    void trackProduct_detectsFlipkartPlatform_andSavesItem() {
        TrackProductRequest request = new TrackProductRequest(
                "https://www.flipkart.com/apple-iphone-12-white-64-gb/p/itm",
                "Apple iPhone 12 (Flipkart)",
                new BigDecimal("50999.00"),
                null,
                "INR",
                null
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        TrackedItem savedItem = new TrackedItem(testUser, request.getTitle(), null);
        savedItem.setId(101L);
        ItemSource savedSource = new ItemSource(
                savedItem,
                Platform.FLIPKART,
                request.getUrl(),
                request.getCurrentPrice(),
                request.getCurrentPrice(),
                "INR",
                null
        );
        savedItem.setSources(new ArrayList<>(List.of(savedSource)));

        when(trackedItemRepository.save(any(TrackedItem.class))).thenReturn(savedItem);

        TrackedItemResponse response = productService.trackProduct(1L, request);

        assertThat(response.getSources().get(0).getPlatform()).isEqualTo(Platform.FLIPKART);
    }

    @Test
    @DisplayName("trackProduct - throws ResourceNotFoundException when user does not exist")
    void trackProduct_throwsException_whenUserNotFound() {
        TrackProductRequest request = new TrackProductRequest(
                "https://www.amazon.in/dp/B08L5WHJ2V",
                "Apple iPhone 12",
                new BigDecimal("49999.00"),
                null,
                "INR",
                null
        );

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.trackProduct(999L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("getTrackedProducts - returns list of items belonging to the user")
    void getTrackedProducts_returnsItemsForAuthenticatedUser() {
        TrackedItem item1 = new TrackedItem(testUser, "Item 1", new BigDecimal("100.00"));
        item1.setId(10L);
        TrackedItem item2 = new TrackedItem(testUser, "Item 2", new BigDecimal("200.00"));
        item2.setId(20L);

        when(trackedItemRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(item1, item2));

        List<TrackedItemResponse> results = productService.getTrackedProducts(1L);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo(10L);
        assertThat(results.get(1).getId()).isEqualTo(20L);
        verify(trackedItemRepository).findByUserIdOrderByCreatedAtDesc(1L);
    }

    @Test
    @DisplayName("deleteTrackedProduct - deletes item when owned by user")
    void deleteTrackedProduct_success_whenOwnedByUser() {
        TrackedItem item = new TrackedItem(testUser, "Item to delete", new BigDecimal("50.00"));
        item.setId(42L);

        when(trackedItemRepository.findByIdAndUserId(42L, 1L)).thenReturn(Optional.of(item));

        productService.deleteTrackedProduct(1L, 42L);

        verify(priceAlertRepository).deleteByTrackedItemId(42L);
        verify(priceHistoryRepository).deleteByItemSourceTrackedItemId(42L);
        verify(trackedItemRepository).delete(item);
    }

    @Test
    @DisplayName("deleteTrackedProduct - throws ResourceNotFoundException when item not owned by user")
    void deleteTrackedProduct_throwsResourceNotFound_whenNotOwned() {
        when(trackedItemRepository.findByIdAndUserId(42L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteTrackedProduct(1L, 42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tracked item not found");

        verify(trackedItemRepository, never()).delete(any());
    }

    @Test
    @DisplayName("refreshProductPrice - scrapes item source URL and records price update")
    void refreshProductPrice_success() {
        TrackedItem item = new TrackedItem(testUser, "Laptop", new BigDecimal("50000.00"));
        item.setId(10L);
        ItemSource source = new ItemSource(item, Platform.AMAZON, "https://www.amazon.in/dp/B08L5WHJ2V", new BigDecimal("55000.00"), new BigDecimal("55000.00"), "INR", null);
        item.addSource(source);

        when(trackedItemRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(item));
        when(priceScraperService.scrapeProduct("https://www.amazon.in/dp/B08L5WHJ2V"))
                .thenReturn(new com.pricehawk.scraper.ScrapedProduct("Laptop", new BigDecimal("52000.00"), "INR", "https://img.jpg", true, Platform.AMAZON));

        TrackedItemResponse response = productService.refreshProductPrice(1L, 10L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        verify(priceScraperService).scrapeProduct("https://www.amazon.in/dp/B08L5WHJ2V");
        verify(priceTrackingService).recordPriceUpdate(eq(source), eq(new BigDecimal("52000.00")), eq(true));
    }

    @Test
    @DisplayName("refreshProductPrice - updates both Amazon and Flipkart sources simultaneously even if one encounters a scraping error")
    void refreshProductPrice_bothSourcesUpdatedEvenIfOneFailsScrape() {
        TrackedItem item = new TrackedItem(testUser, "Laptop", new BigDecimal("50000.00"));
        item.setId(10L);
        ItemSource amazonSource = new ItemSource(item, Platform.AMAZON, "https://www.amazon.in/dp/B08L5WHJ2V", new BigDecimal("55000.00"), new BigDecimal("55000.00"), "INR", null);
        ItemSource flipkartSource = new ItemSource(item, Platform.FLIPKART, "https://www.flipkart.com/laptop", new BigDecimal("54000.00"), new BigDecimal("54000.00"), "INR", null);
        item.addSource(amazonSource);
        item.addSource(flipkartSource);

        when(trackedItemRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(item));
        when(priceScraperService.scrapeProduct("https://www.amazon.in/dp/B08L5WHJ2V"))
                .thenReturn(new com.pricehawk.scraper.ScrapedProduct("Laptop", new BigDecimal("53000.00"), "INR", "https://img.jpg", true, Platform.AMAZON));
        // Simulate Flipkart blocking or failing with 503
        when(priceScraperService.scrapeProduct("https://www.flipkart.com/laptop"))
                .thenThrow(new RuntimeException("503 Service Unavailable"));

        TrackedItemResponse response = productService.refreshProductPrice(1L, 10L);

        assertThat(response).isNotNull();
        // Amazon gets new scraped price
        verify(priceTrackingService).recordPriceUpdate(eq(amazonSource), eq(new BigDecimal("53000.00")), eq(true));
        // Flipkart gets synchronized snapshot with fallback existing price
        verify(priceTrackingService).recordPriceUpdate(eq(flipkartSource), eq(new BigDecimal("54000.00")), eq(true));
    }

    @Test
    @DisplayName("updateTargetPrice - updates target price on item and saves")
    void updateTargetPrice_success() {
        TrackedItem item = new TrackedItem(testUser, "Laptop", new BigDecimal("50000.00"));
        item.setId(10L);

        when(trackedItemRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(item));
        when(trackedItemRepository.save(item)).thenReturn(item);

        TrackedItemResponse response = productService.updateTargetPrice(1L, 10L, new BigDecimal("45000.00"));

        assertThat(response).isNotNull();
        assertThat(item.getTargetPrice()).isEqualByComparingTo(new BigDecimal("45000.00"));
        verify(trackedItemRepository).save(item);
    }

    @Test
    @DisplayName("addProductSource - manual URL adds source and saves")
    void addProductSource_manualUrl_addsSource() {
        TrackedItem item = new TrackedItem(testUser, "Laptop", new BigDecimal("50000.00"));
        item.setId(10L);
        ItemSource source1 = new ItemSource(item, Platform.AMAZON, "https://www.amazon.in/dp/B08L5WHJ2V", new BigDecimal("55000.00"), new BigDecimal("55000.00"), "INR", null);
        item.addSource(source1);

        when(trackedItemRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(item));
        when(trackedItemRepository.save(item)).thenReturn(item);

        com.pricehawk.dto.AddProductSourceRequest req = new com.pricehawk.dto.AddProductSourceRequest(
                "https://www.flipkart.com/laptop/p/itm123?pid=LAPTOP123",
                new BigDecimal("53000.00"),
                "https://img.jpg",
                false
        );

        TrackedItemResponse response = productService.addProductSource(1L, 10L, req);

        assertThat(response).isNotNull();
        assertThat(item.getSources()).hasSize(2);
        assertThat(item.getSources().get(1).getPlatform()).isEqualTo(Platform.FLIPKART);
        verify(trackedItemRepository).save(item);
    }

    @Test
    @DisplayName("addProductSource - autoDiscover finds counterpart product and adds source")
    void addProductSource_autoDiscover_addsCounterpart() {
        TrackedItem item = new TrackedItem(testUser, "iPhone 15", new BigDecimal("65000.00"));
        item.setId(10L);
        ItemSource source1 = new ItemSource(item, Platform.AMAZON, "https://www.amazon.in/dp/B08L5WHJ2V", new BigDecimal("69999.00"), new BigDecimal("69999.00"), "INR", null);
        item.addSource(source1);

        when(trackedItemRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(item));
        when(trackedItemRepository.save(item)).thenReturn(item);
        when(crossPlatformSearchService.findCounterpartProduct(eq(Platform.AMAZON), eq("iPhone 15")))
                .thenReturn(new com.pricehawk.scraper.ScrapedProduct(
                        "iPhone 15 (Black, 128 GB)",
                        new BigDecimal("67990.00"),
                        "INR",
                        "https://img.jpg",
                        "https://www.flipkart.com/iphone-15/p/itm123?pid=IPH15",
                        true,
                        Platform.FLIPKART
                ));

        com.pricehawk.dto.AddProductSourceRequest req = new com.pricehawk.dto.AddProductSourceRequest();
        req.setAutoDiscover(true);

        TrackedItemResponse response = productService.addProductSource(1L, 10L, req);

        assertThat(response).isNotNull();
        assertThat(item.getSources()).hasSize(2);
        assertThat(item.getSources().get(1).getPlatform()).isEqualTo(Platform.FLIPKART);
        assertThat(item.getSources().get(1).getCurrentPrice()).isEqualByComparingTo(new BigDecimal("67990.00"));
        verify(trackedItemRepository).save(item);
    }
}
